const AWS = require("aws-sdk");
const fs = require("fs");
const path = require("path");
const ffmpeg = require("fluent-ffmpeg");
const ffmpegInstaller = require("@ffmpeg-installer/ffmpeg");
const archiver = require("archiver");

// Configura o FFmpeg
ffmpeg.setFfmpegPath(ffmpegInstaller.path);

// Configurar AWS SDK
const s3 = new AWS.S3();
const sqs = new AWS.SQS();

// Função para baixar o vídeo do S3
async function downloadVideoFromS3(bucketName, videoKey, downloadPath) {
  const params = { Bucket: bucketName, Key: videoKey };
  const file = fs.createWriteStream(downloadPath);

  return new Promise((resolve, reject) => {
    s3.getObject(params)
      .createReadStream()
      .pipe(file)
      .on("finish", () => resolve(downloadPath))
      .on("error", reject);
  });
}

// Função para gerar imagens com o intervalo especificado
async function generateThumbnails(videoPath, outputDir, interval) {
  return new Promise((resolve, reject) => {
    ffmpeg(videoPath)
      .on("end", () => {
        console.log("Imagens geradas com sucesso!");
        resolve();
      })
      .on("error", (err) => {
        console.error("Erro ao gerar imagens:", err);
        reject(err);
      })
      .screenshots({
        folder: outputDir,
        filename: "thumbnail-%03d.png",
        timemarks: Array.from({ length: 10 }, (_, i) => (i + 1) * interval), // Gera até 10 imagens
      });
  });
}

// Função para criar um ZIP das imagens
async function createZipFromImages(folderPath, zipPath) {
  return new Promise((resolve, reject) => {
    const output = fs.createWriteStream(zipPath);
    const archive = archiver("zip", { zlib: { level: 9 } });

    output.on("close", () => {
      console.log(`Arquivo ZIP criado com sucesso: ${zipPath}`);
      resolve(zipPath);
    });

    archive.on("error", (err) => {
      console.error("Erro ao criar o ZIP:", err);
      reject(err);
    });

    archive.pipe(output);
    archive.directory(folderPath, false);
    archive.finalize();
  });
}

// Função para fazer upload do ZIP para o S3
async function uploadZipToS3(bucketName, zipKey, zipPath) {
  const fileContent = fs.readFileSync(zipPath);
  const params = {
    Bucket: bucketName,
    Key: zipKey,
    Body: fileContent,
    ContentType: "application/zip",
  };

  return s3.upload(params).promise();
}

// Função para enviar mensagem ao SQS
async function sendMessageToSQS(queueUrl, videoId, zipKey, bucketNameUpload) {
  const params = {
    QueueUrl: queueUrl,
    MessageBody: JSON.stringify({
      job: "App\\Infrastructure\\Jobs\\ProcessCompletedVideo",
      data: {
        status: "completed",
        videoId: videoId,
        zipKey: zipKey,
        bucketName: bucketNameUpload,
      }
    })
  };

  console.log("Enviando mensagem ao SQS:", params);

  return sqs.sendMessage(params).promise();
}

// Handler do AWS Lambda
exports.handler = async (event) => {
  console.log("Evento recebido:", JSON.stringify(event, null, 2));

  for (const record of event.Records) {
    try {
      const messageBody = JSON.parse(record.body);
      const { videoKey, interval, bucketNameDownload, bucketNameUpload, videoId } =
        messageBody;

      if (!videoKey || !interval || !bucketNameDownload || !bucketNameUpload) {
        console.error("Mensagem inválida do SQS:", messageBody);
        continue;
      }

      console.log(
        `Processando vídeo: ${videoKey} com intervalo de ${interval} segundos`
      );

      const tempVideoPath = `/tmp/video.mp4`;
      const outputDir = `/tmp/thumbnails`;
      const zipPath = `/tmp/thumbnails.zip`;
      const zipKey = `output/thumbnails-${Date.now()}.zip`;
      const queueUrl = "https://sqs.us-east-1.amazonaws.com/026131848615/video-completed-queue";

      // Baixar o vídeo do S3
      await downloadVideoFromS3(bucketNameDownload, videoKey, tempVideoPath);

      // Criar pasta de saída se não existir
      if (!fs.existsSync(outputDir)) {
        fs.mkdirSync(outputDir);
      }

      // Gerar imagens
      await generateThumbnails(tempVideoPath, outputDir, interval);

      // Criar arquivo ZIP
      await createZipFromImages(outputDir, zipPath);

      // Fazer upload do ZIP para o S3
      await uploadZipToS3(bucketNameUpload, zipKey, zipPath);

      // Enviar mensagem para o SQS
      await sendMessageToSQS(queueUrl, videoId, zipKey, bucketNameUpload);

      console.log(
        `Processo concluído! ZIP salvo em: ${bucketNameUpload}/${zipKey}`
      );
    } catch (error) {
      console.error("Erro ao processar mensagem do SQS:", error);
    } finally {
      // Limpar arquivos temporários
      if (fs.existsSync(`/tmp/video.mp4`)) fs.unlinkSync(`/tmp/video.mp4`);
      if (fs.existsSync(`/tmp/thumbnails.zip`))
        fs.unlinkSync(`/tmp/thumbnails.zip`);
      if (fs.existsSync(`/tmp/thumbnails`))
        fs.rmSync(`/tmp/thumbnails`, { recursive: true, force: true });
    }
  }
};
