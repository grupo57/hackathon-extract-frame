package br.com.fiap.soat07.hackathon.aws.lambda;

public class Parametros {
    private static final String QUEUE_SUCESSO_KEY = "QUEUE_SUCESSO_KEY";
    private static final String QUEUE_ERRO_KEY = "QUEUE_ERRO_KEY";

    private static String get(String key) {
        if (key == null || key.isEmpty())
            throw new RuntimeException("chave: "+key+" inválida");
        String value = System.getenv(key);
        if (value == null || value.isEmpty())
            throw new RuntimeException("parametro: "+key+" não localizado");
        return value;
    }

    public static String getQueueSucessoUrl() {
        return get(QUEUE_SUCESSO_KEY);
    }

    public static String getQueueErroUrl() {
        return get(QUEUE_ERRO_KEY);
    }

}
