package br.com.cataloghub.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Pedido recebido de um canal e consolidado no hub. */
public class Pedido implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { RECEBIDO, FATURADO, ENVIADO, CANCELADO }

    private int numero;
    private String codigoCanal;
    private Canal canal;
    private String cliente;
    private String cpf;
    private String cidadeUf;
    private final List<ItemPedido> itens = new ArrayList<>();
    private Date data = new Date();
    private Status status = Status.RECEBIDO;
    private Integer numeroNota;

    public double getTotal() {
        double t = 0;
        for (ItemPedido i : itens) t += i.getTotal();
        return t;
    }

    public int getQuantidadeItens() {
        int q = 0;
        for (ItemPedido i : itens) q += i.getQuantidade();
        return q;
    }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getCodigoCanal() { return codigoCanal; }
    public void setCodigoCanal(String codigoCanal) { this.codigoCanal = codigoCanal; }
    public Canal getCanal() { return canal; }
    public void setCanal(Canal canal) { this.canal = canal; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getCidadeUf() { return cidadeUf; }
    public void setCidadeUf(String cidadeUf) { this.cidadeUf = cidadeUf; }
    public List<ItemPedido> getItens() { return itens; }
    public Date getData() { return data; }
    public void setData(Date data) { this.data = data; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Integer getNumeroNota() { return numeroNota; }
    public void setNumeroNota(Integer numeroNota) { this.numeroNota = numeroNota; }
}
