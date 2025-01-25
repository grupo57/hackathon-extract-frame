package br.com.fiap.soat07.hackathon.aws.lambda;

public class Parametros {
    private static final String QUEUE_NOME = "hackathon-video-processado";
    private static final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/744592382994/"+QUEUE_NOME;

    public static String getQueueUrl() {
        return QUEUE_URL;
    }

}
