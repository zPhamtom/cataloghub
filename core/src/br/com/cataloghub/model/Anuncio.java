package br.com.cataloghub.model;

import java.io.Serializable;
import java.util.Date;

/** Publicação de um produto do catálogo central em um canal de venda. */
public class Anuncio implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { SINCRONIZADO, PENDENTE, ERRO, PAUSADO }

    private Canal canal;
    private String sku;
    private String idExterno;
    private String titulo;
    private double preco;
    private int estoquePublicado;
    private Status status = Status.PENDENTE;
    private Date ultimaSync;
    private String mensagem = "";

    public Anuncio(Canal canal, String sku) {
        this.canal = canal;
        this.sku = sku;
    }

    public Canal getCanal() { return canal; }
    public String getSku() { return sku; }
    public String getIdExterno() { return idExterno; }
    public void setIdExterno(String idExterno) { this.idExterno = idExterno; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }
    public int getEstoquePublicado() { return estoquePublicado; }
    public void setEstoquePublicado(int estoquePublicado) { this.estoquePublicado = estoquePublicado; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Date getUltimaSync() { return ultimaSync; }
    public void setUltimaSync(Date ultimaSync) { this.ultimaSync = ultimaSync; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
}
