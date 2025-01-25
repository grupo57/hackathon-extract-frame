package br.com.fiap.soat07.hackathon.aws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class VideoFrameExtractorLambda implements RequestHandler<S3Event, String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoFrameExtractorLambda.class);

    private final S3Client s3Client = S3Client.create();
    private final SqsClient sqsClient = SqsClient.builder().build();

    @Override
    public String handleRequest(S3Event event, Context context) {

        String id = "";
        for (S3EventNotification.S3EventNotificationRecord record : event.getRecords()) {
            String bucketName = record.getS3().getBucket().getName();
            String objectKey = record.getS3().getObject().getKey();

            LocalDateTime inicio = LocalDateTime.now(ZoneId.of("UTC"));
            System.out.println("Arquivo recebido: Bucket = " + bucketName + ", Key = " + objectKey);

            // Faz o download do arquivo
            try {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();

                // Baixando o vídeo para a função Lambda (tem que ser feito em uma pasta temporária, como /tmp)
                System.out.println("Baixando arquivo para: /tmp/video.mp4");
                Path tempFilePath = Paths.get("/tmp/video.mp4");
                s3Client.getObject(getObjectRequest, tempFilePath);
                System.out.println("Arquivo baixado para: /tmp/video.mp4");

                // Gerar frames a cada 30 segundos usando FFmpeg
                System.out.println("Extraindo frames");
                List<Path> imagePaths = extractFramesFromVideo(tempFilePath, context, 30);
                System.out.println(imagePaths.size()+ " Frames extraídos");

                // Criar um arquivo ZIP com todas as imagens geradas
                System.out.println("Gerando zip");
                Path zipFilePath = createZipFile(imagePaths, "/tmp/frames.zip");
                System.out.println("Zip gerado para: /tmp/frames.zip");

                // Fazer o upload do ZIP de volta para o S3
                System.out.println("Uploading arquivo para: fiap-grupo57-hackathon-zip/"+objectKey + "-frames.zip");
                uploadFileToS3(zipFilePath, "fiap-grupo57-hackathon-zip", objectKey + "-frames.zip", context);

                // Limpeza temporária
                cleanUp(tempFilePath, imagePaths, zipFilePath);

                // Após o processamento, envia a mLocalDateTime.now(ZoneId.of("UTC"))ensagem para a SQS
                LocalDateTime termino = LocalDateTime.now(ZoneId.of("UTC"));
                sendMessageToSQS(objectKey+";"+inicio+";"+termino);

                return "Processamento concluído com sucesso!";
            } catch (Exception e) {
                System.err.println("Erro ao baixar o processar o arquivo: " + e.getMessage());
            }
        }

        return "Processamento concluído.";
    }

    // Extrair frames a cada x segundos usando FFmpeg
    private List<Path> extractFramesFromVideo(Path videoPath, Context context, int interval) throws IOException, InterruptedException {
        List<Path> imagePaths = new ArrayList<>();

        // Comando FFmpeg para extrair imagens a cada x segundos
        // Caminho para FFmpeg
        String command = "/opt/bin/ffmpeg -i " + videoPath.toString() + " -vf fps=1/" + interval + " /tmp/frame-%04d.jpg";

        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            // Suponha que os arquivos são gerados como frame-0001.jpg, frame-0002.jpg, etc.
            File tempDir = new File("/tmp");
            File[] files = tempDir.listFiles((dir, name) -> name.startsWith("frame-") && name.endsWith(".jpg"));
            if (files != null) {
                for (File file : files) {
                    imagePaths.add(file.toPath());
                }
            }
        } else {
            throw new IOException("Erro ao executar FFmpeg. Código de saída: " + exitCode);
        }

        return imagePaths;
    }

    // Criar um arquivo ZIP com todas as imagens extraídas
    private Path createZipFile(List<Path> imagePaths, String zipFileName) throws IOException {
        Path zipFilePath = Paths.get(zipFileName);
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            for (Path imagePath : imagePaths) {
                try (FileInputStream fis = new FileInputStream(imagePath.toFile())) {
                    ZipEntry zipEntry = new ZipEntry(imagePath.getFileName().toString());
                    zipOut.putNextEntry(zipEntry);

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, len);
                    }

                    zipOut.closeEntry();
                }
            }
        }

        return zipFilePath;
    }

    // Fazer o upload do arquivo ZIP para o S3
    private void uploadFileToS3(Path zipFilePath, String bucketName, String objectKey, Context context) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        s3Client.putObject(putObjectRequest, zipFilePath);
        context.getLogger().log("Arquivo ZIP carregado para S3: " + objectKey);
    }

    // Limpeza de arquivos temporários
    private void cleanUp(Path videoPath, List<Path> imagePaths, Path zipFilePath) {
        try {
            Files.deleteIfExists(videoPath);
            for (Path imagePath : imagePaths) {
                Files.deleteIfExists(imagePath);
            }
            Files.deleteIfExists(zipFilePath);
        } catch (IOException e) {
            // Ignorar erros de limpeza
        }
    }

    // Envia uma mensagem para a SQS
    private void sendMessageToSQS(String messageBody) {
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(Parametros.getQueueUrl())
                .messageBody(messageBody)
                .build();

        try {
            SendMessageResponse sendMessageResponse = sqsClient.sendMessage(sendMsgRequest);
            System.out.println("Mensagem enviada com ID: " + sendMessageResponse.messageId());
        } catch (SqsException e) {
            throw new RuntimeException("Erro ao enviar mensagem para a fila SQS", e);
        }
    }


    // NOTIFICAR VIA SES
    private void sendEmail() {

    }


}