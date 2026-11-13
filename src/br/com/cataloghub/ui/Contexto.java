package br.com.cataloghub.ui;

import br.com.cataloghub.service.*;

/** Instâncias únicas dos serviços compartilhadas pelas telas. */
public final class Contexto {
    public static final Repositorio REPO = Repositorio.get();
    public static final SyncService SYNC = new SyncService();
    public static final FiscalService FISCAL = new FiscalService();
    public static final PedidoService PEDIDOS = new PedidoService(SYNC, FISCAL);

    private Contexto() { }
}
