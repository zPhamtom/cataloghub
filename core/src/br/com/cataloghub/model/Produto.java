package br.com.cataloghub.model;

import java.io.Serializable;

/** Cadastro central (mestre) do produto. Fonte única da verdade para todos os canais. */
public class Produto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sku;
    private String nome;
    private String marca;
    private String categoria;
    private String descricao;
    private String ean;
    private String ncm;
    private double preco;
    private double custo;
    private int estoque;
    private int estoqueMinimo = 5;
    private double pesoKg;

    public Produto() { }

    public Produto(String sku, String nome, String marca, String categoria, String ncm,
                   double preco, double custo, int estoque, double pesoKg) {
        this.sku = sku;
        this.nome = nome;
        this.marca = marca;
        this.categoria = categoria;
        this.ncm = ncm;
        this.preco = preco;
        this.custo = custo;
        this.estoque = estoque;
        this.pesoKg = pesoKg;
        this.descricao = "";
        this.ean = "";
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getEan() { return ean; }
    public void setEan(String ean) { this.ean = ean; }
    public String getNcm() { return ncm; }
    public void setNcm(String ncm) { this.ncm = ncm; }
    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }
    public double getCusto() { return custo; }
    public void setCusto(double custo) { this.custo = custo; }
    public int getEstoque() { return estoque; }
    public void setEstoque(int estoque) { this.estoque = estoque; }
    public int getEstoqueMinimo() { return estoqueMinimo; }
    public void setEstoqueMinimo(int estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }
    public double getPesoKg() { return pesoKg; }
    public void setPesoKg(double pesoKg) { this.pesoKg = pesoKg; }
}
