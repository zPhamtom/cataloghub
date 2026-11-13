package br.com.cataloghub.ui;

import br.com.cataloghub.model.*;
import br.com.cataloghub.service.IAService;
import br.com.cataloghub.service.Repositorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Visão consolidada de todos os canais + insights da IA. */
public class DashboardPanel extends JPanel implements Atualizavel {

    private final Repositorio repo = Contexto.REPO;
    private final JLabel[] valores = new JLabel[5];
    private final JLabel[] subs = new JLabel[5];
    private final DefaultTableModel modelo = Estilo.modelo("Canal", "Conexão", "Anúncios", "Pedidos",
            "Faturamento", "Comissões (est.)", "Ticket médio");
    private final JTable tabela = Estilo.tabela(modelo);
    private final DefaultListModel<String> insights = new DefaultListModel<>();
    private final Grafico grafico = new Grafico();

    public DashboardPanel() {
        super(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setBackground(Estilo.FUNDO);

        String[] titulos = {"Produtos no catálogo", "Estoque central", "Pedidos", "Faturamento", "NF-e emitidas"};
        JPanel cards = new JPanel(new GridLayout(1, 5, 12, 0));
        cards.setOpaque(false);
        for (int i = 0; i < 5; i++) cards.add(card(titulos[i], i));
        add(cards, BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 2, 12, 0));
        centro.setOpaque(false);

        JPanel esq = new JPanel(new BorderLayout(0, 8));
        esq.setOpaque(false);
        esq.add(secao("Desempenho por canal"), BorderLayout.NORTH);
        JPanel esqConteudo = new JPanel(new GridLayout(2, 1, 0, 10));
        esqConteudo.setOpaque(false);
        esqConteudo.add(Estilo.rolagem(tabela));
        esqConteudo.add(grafico);
        esq.add(esqConteudo, BorderLayout.CENTER);
        centro.add(esq);

        JPanel dir = new JPanel(new BorderLayout(0, 8));
        dir.setOpaque(false);
        dir.add(secao("Assistente IA - Insights automáticos"), BorderLayout.NORTH);
        JList<String> lista = new JList<>(insights);
        lista.setFont(Estilo.NORMAL);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                String t = String.valueOf(v);
                lb.setText("<html><body style='width:420px;padding:4px'>" + t + "</body></html>");
                if (!s) {
                    if (t.startsWith("[CRÍTICO]")) lb.setForeground(Estilo.VERMELHO);
                    else if (t.startsWith("[ESTOQUE]") || t.startsWith("[INTEGRAÇÃO]")) lb.setForeground(Estilo.LARANJA);
                    else if (t.startsWith("[PREÇO]")) lb.setForeground(Estilo.AZUL);
                    else if (t.startsWith("[VENDAS]")) lb.setForeground(Estilo.VERDE);
                    else lb.setForeground(Color.DARK_GRAY);
                }
                lb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 235, 235)));
                return lb;
            }
        });
        dir.add(Estilo.rolagem(lista), BorderLayout.CENTER);
        dir.add(Estilo.barra(
                Estilo.botaoPrimario("Sincronizar todos os canais", () -> {
                    int[] r = Contexto.SYNC.sincronizarTudo();
                    repo.alterado();
                    JOptionPane.showMessageDialog(this, r[0] + " anúncio(s) sincronizado(s), " + r[1] + " com erro/pendente.");
                }),
                Estilo.botao("Simular pedido de marketplace", () -> {
                    Pedido p = Contexto.PEDIDOS.simularPedido();
                    repo.alterado();
                    if (p == null) JOptionPane.showMessageDialog(this, "Nenhum anúncio com estoque disponível para gerar pedido.");
                })
        ), BorderLayout.SOUTH);
        centro.add(dir);
        add(centro, BorderLayout.CENTER);
        atualizar();
    }

    private JComponent secao(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Segoe UI", Font.BOLD, 15));
        l.setForeground(Estilo.PRIMARIA_ESCURA);
        return l;
    }

    private JPanel card(String titulo, int i) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, Estilo.PRIMARIA),
                new EmptyBorder(10, 14, 10, 14)));
        JLabel t = new JLabel(titulo);
        t.setFont(Estilo.NORMAL);
        t.setForeground(Estilo.TEXTO_SUAVE);
        valores[i] = new JLabel("-");
        valores[i].setFont(new Font("Segoe UI", Font.BOLD, 24));
        subs[i] = new JLabel(" ");
        subs[i].setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subs[i].setForeground(Estilo.TEXTO_SUAVE);
        p.add(t);
        p.add(valores[i]);
        p.add(subs[i]);
        return p;
    }

    @Override
    public void atualizar() {
        int estoque = 0, semEstoque = 0;
        for (Produto p : repo.getProdutos()) {
            estoque += p.getEstoque();
            if (p.getEstoque() == 0) semEstoque++;
        }
        int pedidosValidos = 0, pendentes = 0;
        double fat = 0;
        Map<Canal, double[]> porCanal = new EnumMap<>(Canal.class); // {pedidos, faturamento}
        for (Canal c : Canal.values()) porCanal.put(c, new double[2]);
        for (Pedido p : repo.getPedidos()) {
            if (p.getStatus() == Pedido.Status.CANCELADO) continue;
            pedidosValidos++;
            if (p.getStatus() == Pedido.Status.RECEBIDO) pendentes++;
            fat += p.getTotal();
            double[] d = porCanal.get(p.getCanal());
            d[0]++;
            d[1] += p.getTotal();
        }
        int nfs = 0;
        for (NotaFiscal n : repo.getNotas()) if (n.getStatus() == NotaFiscal.Status.AUTORIZADA) nfs++;

        valores[0].setText(String.valueOf(repo.getProdutos().size()));
        subs[0].setText(repo.getAnuncios().size() + " anúncios ativos em " + Canal.values().length + " canais");
        valores[1].setText(estoque + " un.");
        subs[1].setText(semEstoque > 0 ? semEstoque + " produto(s) sem estoque" : "Sincronizado em todos os canais");
        valores[2].setText(String.valueOf(pedidosValidos));
        subs[2].setText(pendentes + " aguardando faturamento");
        valores[3].setText(Estilo.moeda(fat));
        subs[3].setText("Todos os canais");
        valores[4].setText(String.valueOf(nfs));
        subs[4].setText(repo.isFaturamentoAutomatico() ? "Emissão automática ATIVA" : "Emissão manual");

        List<Object[]> linhas = new ArrayList<>();
        for (Canal c : Canal.values()) {
            int an = 0;
            for (Anuncio a : repo.getAnuncios()) if (a.getCanal() == c) an++;
            double[] d = porCanal.get(c);
            linhas.add(new Object[]{c.getNome(), repo.conectado(c) ? "Conectado" : "Desconectado", an, (int) d[0],
                    Estilo.moeda(d[1]), Estilo.moeda(d[1] * c.getComissao() + d[0] * c.getTaxaFixa()),
                    d[0] > 0 ? Estilo.moeda(d[1] / d[0]) : "-"});
        }
        Estilo.recarregar(tabela, modelo, linhas);
        grafico.setDados(porCanal);

        insights.clear();
        for (String s : IAService.insights(repo)) insights.addElement(s);
    }

    /** Gráfico de barras simples (faturamento por canal) desenhado com Java2D. */
    static class Grafico extends JPanel {
        private Map<Canal, double[]> dados = new EnumMap<>(Canal.class);

        Grafico() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(220, 224, 228)));
        }

        void setDados(Map<Canal, double[]> d) { dados = d; repaint(); }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Estilo.NEGRITO);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Faturamento por canal", 12, 20);
            double max = 1;
            for (double[] d : dados.values()) max = Math.max(max, d[1]);
            int n = Canal.values().length;
            int topo = 34, esq = 110, dir = 20;
            int alturaLinha = Math.max(16, (getHeight() - topo - 10) / n);
            int larguraMax = getWidth() - esq - dir - 90;
            int i = 0;
            for (Canal c : Canal.values()) {
                double v = dados.containsKey(c) ? dados.get(c)[1] : 0;
                int y = topo + i * alturaLinha;
                int h = Math.max(8, alturaLinha - 8);
                g.setFont(Estilo.NORMAL);
                g.setColor(Color.DARK_GRAY);
                g.drawString(c.getNome(), 12, y + h / 2 + 5);
                int w = (int) (larguraMax * (v / max));
                g.setColor(new Color(c.getCor()));
                g.fillRoundRect(esq, y, Math.max(w, 2), h, 6, 6);
                g.setColor(new Color(0, 0, 0, 40));
                g.drawRoundRect(esq, y, Math.max(w, 2), h, 6, 6);
                g.setColor(Color.DARK_GRAY);
                g.drawString(Estilo.moeda(v), esq + w + 8, y + h / 2 + 5);
                i++;
            }
        }
    }
}
