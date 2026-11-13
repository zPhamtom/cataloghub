package br.com.cataloghub.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Utilitários visuais compartilhados pelas telas. */
public final class Estilo {

    public static final Color PRIMARIA = new Color(0, 150, 110);
    public static final Color PRIMARIA_ESCURA = new Color(0, 105, 80);
    public static final Color FUNDO = new Color(245, 247, 250);
    public static final Color TEXTO_SUAVE = new Color(100, 110, 120);
    public static final Color VERDE = new Color(30, 140, 60);
    public static final Color VERMELHO = new Color(200, 40, 40);
    public static final Color LARANJA = new Color(220, 120, 0);
    public static final Color AZUL = new Color(30, 100, 200);
    public static final Color CINZA = new Color(130, 130, 130);

    public static final Font TITULO = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font NEGRITO = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font MONO = new Font("Consolas", Font.PLAIN, 13);

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    public static final SimpleDateFormat DATA_HORA = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private Estilo() { }

    public static String moeda(double v) { return MOEDA.format(v); }

    public static String pct(double v) { return String.format("%.1f%%", v * 100); }

    public static JButton botao(String texto, Runnable acao) {
        JButton b = new JButton(texto);
        b.setFont(NORMAL);
        b.setFocusPainted(false);
        b.addActionListener(e -> acao.run());
        return b;
    }

    public static JButton botaoPrimario(String texto, Runnable acao) {
        JButton b = botao(texto, acao);
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI()); // o L&F do Windows ignora a cor de fundo
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(NEGRITO);
        b.setBackground(PRIMARIA);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setBorder(new EmptyBorder(6, 14, 6, 14));
        return b;
    }

    public static JPanel barra(Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        p.setOpaque(false);
        for (Component c : comps) p.add(c);
        return p;
    }

    public static DefaultTableModel modelo(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    public static JTable tabela(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setRowHeight(26);
        t.setFont(NORMAL);
        t.getTableHeader().setFont(NEGRITO);
        t.getTableHeader().setReorderingAllowed(false);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setShowVerticalLines(false);
        t.setGridColor(new Color(230, 230, 230));
        t.setFillsViewportHeight(true);
        t.setDefaultRenderer(Object.class, new StatusRenderer());
        return t;
    }

    /** Recarrega as linhas mantendo a seleção (pela 1ª coluna). */
    public static void recarregar(JTable t, DefaultTableModel m, List<Object[]> linhas) {
        int sel = t.getSelectedRow();
        Object chave = sel >= 0 && sel < m.getRowCount() ? m.getValueAt(sel, 0) : null;
        Object chave2 = sel >= 0 && sel < m.getRowCount() && m.getColumnCount() > 2 ? m.getValueAt(sel, 2) : null;
        m.setRowCount(0);
        for (Object[] l : linhas) m.addRow(l);
        if (chave != null) {
            for (int i = 0; i < m.getRowCount(); i++) {
                if (chave.equals(m.getValueAt(i, 0)) && (chave2 == null || chave2.equals(m.getValueAt(i, 2)))) {
                    t.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    public static void larguras(JTable t, int... w) {
        for (int i = 0; i < w.length && i < t.getColumnCount(); i++) t.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
    }

    public static JScrollPane rolagem(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 228)));
        return s;
    }

    public static void mostrarTexto(Component pai, String titulo, String texto) {
        JTextArea a = new JTextArea(texto);
        a.setFont(MONO);
        a.setEditable(false);
        a.setCaretPosition(0);
        JScrollPane s = new JScrollPane(a);
        s.setPreferredSize(new Dimension(780, 520));
        JOptionPane.showMessageDialog(pai, s, titulo, JOptionPane.PLAIN_MESSAGE);
    }

    /** Pinta células de status com cores semânticas e linhas zebradas. */
    static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(new EmptyBorder(0, 6, 0, 6));
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 251));
                c.setForeground(Color.DARK_GRAY);
            }
            setFont(NORMAL);
            String s = value == null ? "" : value.toString();
            Color cor = corStatus(s);
            if (cor != null) {
                setFont(NEGRITO);
                if (!isSelected) c.setForeground(cor);
            }
            return c;
        }
    }

    static Color corStatus(String s) {
        switch (s) {
            case "SINCRONIZADO": case "AUTORIZADA": case "ENVIADO": case "OK": case "Conectado": return VERDE;
            case "ERRO": case "CANCELADA": case "CANCELADO": case "SEM ESTOQUE": case "Desconectado": return VERMELHO;
            case "PENDENTE": case "RECEBIDO": case "BAIXO": return LARANJA;
            case "FATURADO": return AZUL;
            case "PAUSADO": return CINZA;
            default: return null;
        }
    }
}
