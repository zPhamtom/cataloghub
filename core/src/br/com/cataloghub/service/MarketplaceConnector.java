package br.com.cataloghub.service;

import br.com.cataloghub.model.Anuncio;
import br.com.cataloghub.model.Canal;

/**
 * Contrato de integração com um canal de venda (padrão Adapter).
 * Cada marketplace real teria sua implementação chamando a API oficial (REST/OAuth2).
 * Nesta demonstração todas usam {@link MockMarketplaceConnector}.
 */
public interface MarketplaceConnector {

    Canal getCanal();

    /** Cria o anúncio no canal e devolve o ID externo gerado pelo marketplace. */
    String publicar(Anuncio anuncio) throws IntegracaoException;

    /** Atualiza preço e estoque de um anúncio existente. */
    void atualizar(Anuncio anuncio) throws IntegracaoException;

    /** Pausa/despublica o anúncio no canal. */
    void pausar(Anuncio anuncio) throws IntegracaoException;

    class IntegracaoException extends Exception {
        public IntegracaoException(String msg) { super(msg); }
    }
}
