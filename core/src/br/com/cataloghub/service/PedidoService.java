package br.com.cataloghub.service;

import br.com.cataloghub.model.*;

import java.util.*;

/**
 * Recebimento e ciclo de vida dos pedidos vindos dos canais.
 * Fluxo automático: pedido recebido -> baixa no estoque central -> sincroniza todos os canais
 *                   -> emite NF-e (se faturamento automático ativo).
 */
public class PedidoService {

    private static final String[] NOMES = {"Ana Souza", "Bruno Lima", "Carla Mendes", "Diego Rocha", "Elisa Prado",
            "Felipe Arruda", "Gabriela Torres", "Henrique Alves", "Isabela Nunes", "João Pereira", "Karina Duarte",
            "Lucas Martins", "Mariana Costa", "Nicolas Ribeiro", "Olívia Santos", "Paulo Henrique", "Rafaela Gomes"};
    private static final String[] CIDADES = {"São Paulo/SP", "Rio de Janeiro/RJ", "Belo Horizonte/MG", "Curitiba/PR",
            "Porto Alegre/RS", "Salvador/BA", "Recife/PE", "Fortaleza/CE", "Goiânia/GO", "Campinas/SP", "Florianópolis/SC"};

    private final Repositorio repo = Repositorio.get();
    private final SyncService sync;
    private final FiscalService fiscal;
    private final Random rnd = new Random();

    public PedidoService(SyncService sync, FiscalService fiscal) {
        this.sync = sync;
        this.fiscal = fiscal;
    }

    /** Gera um pedido aleatório como se tivesse chegado via webhook de um marketplace. */
    public Pedido simularPedido() {
        List<Anuncio> elegiveis = new ArrayList<>();
        for (Anuncio a : repo.getAnuncios()) {
            if (a.getStatus() == Anuncio.Status.SINCRONIZADO && a.getEstoquePublicado() > 0 && repo.conectado(a.getCanal()))
                elegiveis.add(a);
        }
        if (elegiveis.isEmpty()) return null;
        Anuncio principal = elegiveis.get(rnd.nextInt(elegiveis.size()));
        Canal canal = principal.getCanal();

        Pedido p = new Pedido();
        p.setCanal(canal);
        p.setCliente(NOMES[rnd.nextInt(NOMES.length)]);
        p.setCpf(gerarCpf());
        p.setCidadeUf(CIDADES[rnd.nextInt(CIDADES.length)]);
        adicionarItem(p, principal);
        // às vezes o cliente leva mais um item do mesmo canal
        if (rnd.nextInt(3) == 0) {
            for (Anuncio a : elegiveis) {
                if (a.getCanal() == canal && !a.getSku().equals(principal.getSku())) { adicionarItem(p, a); break; }
            }
        }
        return registrar(p) ? p : null;
    }

    private void adicionarItem(Pedido p, Anuncio a) {
        Produto prod = repo.produto(a.getSku());
        int qtd = 1 + (rnd.nextInt(4) == 0 ? 1 : 0);
        qtd = Math.min(qtd, Math.max(1, a.getEstoquePublicado()));
        p.getItens().add(new ItemPedido(prod.getSku(), prod.getNome(), prod.getNcm(), qtd, a.getPreco()));
    }

    /** Valida estoque, baixa, sincroniza e fatura. */
    public boolean registrar(Pedido p) {
        for (ItemPedido i : p.getItens()) {
            Produto prod = repo.produto(i.getSku());
            if (prod == null || prod.getEstoque() < i.getQuantidade()) {
                repo.log(Evento.Tipo.PEDIDO, "Pedido do " + p.getCanal() + " RECUSADO: estoque insuficiente de " + i.getSku()
                        + " (overselling evitado).");
                return false;
            }
        }
        p.setNumero(repo.novoNumeroPedido());
        p.setCodigoCanal(p.getCanal().getPrefixo() + "-" + (2000000000L + (long) (rnd.nextDouble() * 999999999L)));
        repo.getPedidos().add(p);
        repo.log(Evento.Tipo.PEDIDO, String.format("Pedido #%d recebido de %s (%s) - %d item(ns) - R$ %.2f",
                p.getNumero(), p.getCanal(), p.getCodigoCanal(), p.getQuantidadeItens(), p.getTotal()));

        for (ItemPedido i : p.getItens()) {
            Produto prod = repo.produto(i.getSku());
            prod.setEstoque(prod.getEstoque() - i.getQuantidade());
            sync.sincronizarProduto(prod);
            repo.log(Evento.Tipo.SYNC, "Estoque de " + prod.getSku() + " atualizado para " + prod.getEstoque()
                    + " e propagado para " + repo.anunciosDoProduto(prod.getSku()).size() + " canal(is).");
        }
        if (repo.isFaturamentoAutomatico()) fiscal.emitir(p);
        return true;
    }

    public NotaFiscal faturar(Pedido p) {
        if (p.getStatus() == Pedido.Status.CANCELADO) return null;
        return fiscal.emitir(p);
    }

    public void marcarEnviado(Pedido p) {
        if (p.getStatus() == Pedido.Status.FATURADO) {
            p.setStatus(Pedido.Status.ENVIADO);
            repo.log(Evento.Tipo.PEDIDO, "Pedido #" + p.getNumero() + " enviado. Rastreio informado ao " + p.getCanal() + ".");
        }
    }

    public void cancelar(Pedido p) {
        if (p.getStatus() == Pedido.Status.CANCELADO) return;
        p.setStatus(Pedido.Status.CANCELADO);
        for (ItemPedido i : p.getItens()) {
            Produto prod = repo.produto(i.getSku());
            if (prod != null) {
                prod.setEstoque(prod.getEstoque() + i.getQuantidade());
                sync.sincronizarProduto(prod);
            }
        }
        if (p.getNumeroNota() != null) fiscal.cancelar(repo.nota(p.getNumeroNota()));
        repo.log(Evento.Tipo.PEDIDO, "Pedido #" + p.getNumero() + " cancelado. Estoque devolvido e canais sincronizados.");
    }

    private String gerarCpf() {
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) d[i] = rnd.nextInt(10);
        for (int t = 9; t < 11; t++) {
            int soma = 0;
            for (int i = 0; i < t; i++) soma += d[i] * ((t + 1) - i);
            int r = (soma * 10) % 11;
            d[t] = r == 10 ? 0 : r;
        }
        return String.format("%d%d%d.%d%d%d.%d%d%d-%d%d", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10]);
    }
}
