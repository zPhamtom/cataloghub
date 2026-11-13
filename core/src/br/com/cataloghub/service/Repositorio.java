package br.com.cataloghub.service;

import br.com.cataloghub.model.*;

import java.io.*;
import java.util.*;

/**
 * Estado da aplicação + persistência local (arquivo serializado em ./dados/cataloghub.dat).
 * Em uma solução real seria um banco de dados em nuvem (ex.: PostgreSQL gerenciado).
 */
public class Repositorio implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Pasta dos dados: "./dados" no desktop; no Android, a pasta interna do app. */
    private static File pastaDados = new File("dados");
    private static Repositorio instancia;

    /** Deve ser chamado antes do primeiro get() (o Android informa getFilesDir()). */
    public static void setPastaDados(File pasta) { pastaDados = pasta; }

    private static File arquivo() { return new File(pastaDados, "cataloghub.dat"); }

    private final List<Produto> produtos = new ArrayList<>();
    private final List<Anuncio> anuncios = new ArrayList<>();
    private final List<Pedido> pedidos = new ArrayList<>();
    private final List<NotaFiscal> notas = new ArrayList<>();
    private final List<Evento> eventos = new ArrayList<>();
    private final EnumMap<Canal, Boolean> canaisConectados = new EnumMap<>(Canal.class);
    private int proximoPedido = 1001;
    private int proximaNota = 1;
    private boolean faturamentoAutomatico = true;

    private transient List<Runnable> listeners;

    public static synchronized Repositorio get() {
        if (instancia == null) instancia = carregar();
        return instancia;
    }

    private static Repositorio carregar() {
        File arq = arquivo();
        if (arq.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(arq))) {
                return (Repositorio) in.readObject();
            } catch (Exception e) {
                System.err.println("Falha ao ler base, recriando: " + e.getMessage());
            }
        }
        Repositorio r = new Repositorio();
        for (Canal c : Canal.values()) r.canaisConectados.put(c, true);
        r.log(Evento.Tipo.SISTEMA, "Base de dados criada.");
        return r;
    }

    public synchronized void salvar() {
        try {
            File arq = arquivo();
            arq.getParentFile().mkdirs();
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(arq))) {
                out.writeObject(this);
            }
        } catch (IOException e) {
            System.err.println("Falha ao salvar: " + e.getMessage());
        }
    }

    /** Apaga todos os dados (usado no botão "Resetar demonstração"). */
    public void resetar() {
        produtos.clear(); anuncios.clear(); pedidos.clear(); notas.clear(); eventos.clear();
        for (Canal c : Canal.values()) canaisConectados.put(c, true);
        proximoPedido = 1001;
        proximaNota = 1;
        faturamentoAutomatico = true;
        log(Evento.Tipo.SISTEMA, "Demonstração resetada.");
    }

    // ---------- notificação da UI ----------
    public void addListener(Runnable r) {
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        if (listeners != null) listeners.remove(r);
    }

    /** Persiste e avisa as telas que algo mudou. */
    public void alterado() {
        salvar();
        if (listeners != null) for (Runnable r : listeners) r.run();
    }

    public void log(Evento.Tipo tipo, String msg) {
        eventos.add(new Evento(tipo, msg));
        if (eventos.size() > 2000) eventos.remove(0);
    }

    // ---------- consultas ----------
    public Produto produto(String sku) {
        for (Produto p : produtos) if (p.getSku().equalsIgnoreCase(sku)) return p;
        return null;
    }

    public Anuncio anuncio(String sku, Canal canal) {
        for (Anuncio a : anuncios) if (a.getSku().equalsIgnoreCase(sku) && a.getCanal() == canal) return a;
        return null;
    }

    public List<Anuncio> anunciosDoProduto(String sku) {
        List<Anuncio> l = new ArrayList<>();
        for (Anuncio a : anuncios) if (a.getSku().equalsIgnoreCase(sku)) l.add(a);
        return l;
    }

    public Pedido pedido(int numero) {
        for (Pedido p : pedidos) if (p.getNumero() == numero) return p;
        return null;
    }

    public NotaFiscal nota(int numero) {
        for (NotaFiscal n : notas) if (n.getNumero() == numero) return n;
        return null;
    }

    public boolean conectado(Canal c) {
        Boolean b = canaisConectados.get(c);
        return b != null && b;
    }

    public void setConectado(Canal c, boolean v) { canaisConectados.put(c, v); }

    public int novoNumeroPedido() { return proximoPedido++; }
    public int novoNumeroNota() { return proximaNota++; }

    public List<Produto> getProdutos() { return produtos; }
    public List<Anuncio> getAnuncios() { return anuncios; }
    public List<Pedido> getPedidos() { return pedidos; }
    public List<NotaFiscal> getNotas() { return notas; }
    public List<Evento> getEventos() { return eventos; }
    public boolean isFaturamentoAutomatico() { return faturamentoAutomatico; }
    public void setFaturamentoAutomatico(boolean v) { this.faturamentoAutomatico = v; }
}
