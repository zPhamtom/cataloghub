package br.com.cataloghub.service;

import br.com.cataloghub.model.*;

import java.util.*;

/**
 * Sincronização catálogo central -> canais.
 * Regra principal: o estoque central é a fonte da verdade; qualquer alteração (venda, ajuste,
 * cancelamento) é propagada imediatamente para todos os canais, evitando venda sem estoque (overselling).
 */
public class SyncService {

    private final Repositorio repo = Repositorio.get();
    private final Map<Canal, MarketplaceConnector> conectores = new EnumMap<>(Canal.class);

    public SyncService() {
        for (Canal c : Canal.values()) conectores.put(c, new MockMarketplaceConnector(c));
    }

    /** Publica (ou republica) o produto nos canais escolhidos usando título e preço gerados pela IA. */
    public int publicar(Produto p, Collection<Canal> canais, boolean usarPrecoSugerido) {
        int ok = 0;
        for (Canal c : canais) {
            Anuncio a = repo.anuncio(p.getSku(), c);
            if (a == null) {
                a = new Anuncio(c, p.getSku());
                repo.getAnuncios().add(a);
            }
            a.setTitulo(IAService.gerarTitulo(p, c));
            a.setPreco(usarPrecoSugerido ? IAService.sugerirPreco(p, c, IAService.MARGEM_ALVO) : p.getPreco());
            a.setEstoquePublicado(p.getEstoque());
            if (enviar(a, a.getIdExterno() == null)) ok++;
        }
        repo.log(Evento.Tipo.CATALOGO, "Produto " + p.getSku() + " publicado em " + ok + "/" + canais.size() + " canal(is).");
        return ok;
    }

    /** Remove a publicação do canal. */
    public void despublicar(Anuncio a) {
        try {
            if (repo.conectado(a.getCanal()) && a.getIdExterno() != null) conectores.get(a.getCanal()).pausar(a);
        } catch (MarketplaceConnector.IntegracaoException ignored) { }
        repo.getAnuncios().remove(a);
        repo.log(Evento.Tipo.SYNC, "Anúncio " + a.getSku() + " removido de " + a.getCanal());
    }

    /** Propaga estoque/preço de um produto para todos os canais onde está anunciado. */
    public void sincronizarProduto(Produto p) {
        for (Anuncio a : repo.anunciosDoProduto(p.getSku())) {
            if (a.getStatus() == Anuncio.Status.PAUSADO) continue;
            a.setEstoquePublicado(p.getEstoque());
            enviar(a, a.getIdExterno() == null);
        }
    }

    /** Sincroniza todos os anúncios. Retorna {ok, erro}. */
    public int[] sincronizarTudo() {
        int ok = 0, erro = 0;
        for (Anuncio a : new ArrayList<>(repo.getAnuncios())) {
            if (a.getStatus() == Anuncio.Status.PAUSADO) continue;
            Produto p = repo.produto(a.getSku());
            if (p == null) continue;
            a.setEstoquePublicado(p.getEstoque());
            if (enviar(a, a.getIdExterno() == null)) ok++; else erro++;
        }
        repo.log(Evento.Tipo.SYNC, "Sincronização geral: " + ok + " ok, " + erro + " com erro/pendente.");
        return new int[]{ok, erro};
    }

    /** Reprocessa apenas anúncios com erro ou pendentes (fila de retry). */
    public int reprocessarErros() {
        int ok = 0;
        for (Anuncio a : repo.getAnuncios()) {
            if (a.getStatus() == Anuncio.Status.ERRO || a.getStatus() == Anuncio.Status.PENDENTE) {
                Produto p = repo.produto(a.getSku());
                if (p != null) a.setEstoquePublicado(p.getEstoque());
                if (enviar(a, a.getIdExterno() == null)) ok++;
            }
        }
        repo.log(Evento.Tipo.SYNC, "Reprocessamento: " + ok + " anúncio(s) recuperado(s).");
        return ok;
    }

    public void pausarOuAtivar(Anuncio a) {
        if (a.getStatus() == Anuncio.Status.PAUSADO) {
            a.setStatus(Anuncio.Status.PENDENTE);
            Produto p = repo.produto(a.getSku());
            if (p != null) a.setEstoquePublicado(p.getEstoque());
            enviar(a, a.getIdExterno() == null);
            repo.log(Evento.Tipo.SYNC, "Anúncio " + a.getSku() + " reativado em " + a.getCanal());
        } else {
            try {
                if (repo.conectado(a.getCanal())) conectores.get(a.getCanal()).pausar(a);
                a.setStatus(Anuncio.Status.PAUSADO);
                a.setEstoquePublicado(0);
                a.setMensagem("Pausado pelo lojista");
                repo.log(Evento.Tipo.SYNC, "Anúncio " + a.getSku() + " pausado em " + a.getCanal());
            } catch (MarketplaceConnector.IntegracaoException e) {
                a.setMensagem(e.getMessage());
                repo.log(Evento.Tipo.ERRO, "Falha ao pausar " + a.getSku() + " em " + a.getCanal() + ": " + e.getMessage());
            }
        }
    }

    private boolean enviar(Anuncio a, boolean novo) {
        if (!repo.conectado(a.getCanal())) {
            a.setStatus(Anuncio.Status.PENDENTE);
            a.setMensagem("Canal desconectado - aguardando reconexão");
            return false;
        }
        MarketplaceConnector con = conectores.get(a.getCanal());
        try {
            if (novo) a.setIdExterno(con.publicar(a));
            else con.atualizar(a);
            a.setStatus(Anuncio.Status.SINCRONIZADO);
            a.setUltimaSync(new Date());
            a.setMensagem("OK");
            return true;
        } catch (MarketplaceConnector.IntegracaoException e) {
            a.setStatus(Anuncio.Status.ERRO);
            a.setMensagem(e.getMessage());
            repo.log(Evento.Tipo.ERRO, a.getCanal() + " / " + a.getSku() + ": " + e.getMessage());
            return false;
        }
    }
}
