package br.com.cataloghub.model;

import java.io.Serializable;
import java.util.Date;

/** Registro de auditoria/integração exibido na aba de logs. */
public class Evento implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tipo { CATALOGO, SYNC, PEDIDO, FISCAL, IA, ERRO, SISTEMA }

    private final Date data = new Date();
    private final Tipo tipo;
    private final String mensagem;

    public Evento(Tipo tipo, String mensagem) {
        this.tipo = tipo;
        this.mensagem = mensagem;
    }

    public Date getData() { return data; }
    public Tipo getTipo() { return tipo; }
    public String getMensagem() { return mensagem; }
}
