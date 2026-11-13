package br.com.cataloghub.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** NF-e simulada (modelo 55) — gerada automaticamente a partir do pedido. Sem valor fiscal. */
public class NotaFiscal implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { AUTORIZADA, CANCELADA }

    private int numero;
    private int serie = 1;
    private String chave;
    private String protocolo;
    private Date dataEmissao = new Date();
    private int pedidoNumero;
    private Canal canal;
    private String cliente;
    private String cpf;
    private String cidadeUf;
    private final List<ItemPedido> itens = new ArrayList<>();
    private double valorProdutos;
    private double icms;
    private double pis;
    private double cofins;
    private Status status = Status.AUTORIZADA;

    public double getTributosAprox() { return icms + pis + cofins; }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public int getSerie() { return serie; }
    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }
    public String getProtocolo() { return protocolo; }
    public void setProtocolo(String protocolo) { this.protocolo = protocolo; }
    public Date getDataEmissao() { return dataEmissao; }
    public int getPedidoNumero() { return pedidoNumero; }
    public void setPedidoNumero(int pedidoNumero) { this.pedidoNumero = pedidoNumero; }
    public Canal getCanal() { return canal; }
    public void setCanal(Canal canal) { this.canal = canal; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getCidadeUf() { return cidadeUf; }
    public void setCidadeUf(String cidadeUf) { this.cidadeUf = cidadeUf; }
    public List<ItemPedido> getItens() { return itens; }
    public double getValorProdutos() { return valorProdutos; }
    public void setValorProdutos(double valorProdutos) { this.valorProdutos = valorProdutos; }
    public double getIcms() { return icms; }
    public void setIcms(double icms) { this.icms = icms; }
    public double getPis() { return pis; }
    public void setPis(double pis) { this.pis = pis; }
    public double getCofins() { return cofins; }
    public void setCofins(double cofins) { this.cofins = cofins; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
