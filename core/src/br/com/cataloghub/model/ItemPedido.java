package br.com.cataloghub.model;

import java.io.Serializable;

public class ItemPedido implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sku;
    private final String descricao;
    private final String ncm;
    private final int quantidade;
    private final double precoUnitario;

    public ItemPedido(String sku, String descricao, String ncm, int quantidade, double precoUnitario) {
        this.sku = sku;
        this.descricao = descricao;
        this.ncm = ncm;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
    }

    public String getSku() { return sku; }
    public String getDescricao() { return descricao; }
    public String getNcm() { return ncm; }
    public int getQuantidade() { return quantidade; }
    public double getPrecoUnitario() { return precoUnitario; }
    public double getTotal() { return quantidade * precoUnitario; }
}
