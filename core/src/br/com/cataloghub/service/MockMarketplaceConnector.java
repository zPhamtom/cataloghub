package br.com.cataloghub.service;

import br.com.cataloghub.model.Anuncio;
import br.com.cataloghub.model.Canal;

import java.util.Random;

/**
 * Conector simulado: imita respostas de uma API de marketplace, inclusive validações
 * e falhas intermitentes (timeout / rate limit), para demonstrar o tratamento de erros e reprocessamento.
 */
public class MockMarketplaceConnector implements MarketplaceConnector {

    /** Probabilidade de falha simulada (0.0 a 1.0). */
    public static double taxaFalha = 0.06;

    private static final String[] ERROS = {
            "HTTP 429 - Too Many Requests (rate limit do canal)",
            "HTTP 504 - Gateway Timeout",
            "HTTP 503 - Serviço do canal indisponível"
    };

    private final Canal canal;
    private final Random rnd = new Random();

    public MockMarketplaceConnector(Canal canal) { this.canal = canal; }

    @Override
    public Canal getCanal() { return canal; }

    @Override
    public String publicar(Anuncio a) throws IntegracaoException {
        validar(a);
        falhaAleatoria();
        long id = 100000000L + (long) (rnd.nextDouble() * 899999999L);
        return canal.getPrefixo() + id;
    }

    @Override
    public void atualizar(Anuncio a) throws IntegracaoException {
        validar(a);
        falhaAleatoria();
    }

    @Override
    public void pausar(Anuncio a) throws IntegracaoException {
        falhaAleatoria();
    }

    private void validar(Anuncio a) throws IntegracaoException {
        if (a.getTitulo() == null || a.getTitulo().trim().length() < 5)
            throw new IntegracaoException("HTTP 400 - Título muito curto");
        if (a.getTitulo().length() > canal.getMaxTitulo())
            throw new IntegracaoException("HTTP 400 - Título excede " + canal.getMaxTitulo() + " caracteres");
        if (a.getPreco() <= 0)
            throw new IntegracaoException("HTTP 400 - Preço inválido");
    }

    private void falhaAleatoria() throws IntegracaoException {
        if (rnd.nextDouble() < taxaFalha) throw new IntegracaoException(ERROS[rnd.nextInt(ERROS.length)]);
    }
}
