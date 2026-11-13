package br.com.cataloghub.ui;

import br.com.cataloghub.model.*;
import br.com.cataloghub.service.Repositorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Pedidos de todos os canais em uma única fila. */
public class PedidosPanel extends JPanel implements Atualizavel {

    private final Repositorio repo = Contexto.REPO;
    private final DefaultTableModel modelo = Estilo.modelo("Nº", "Canal", "Código no canal", "Cliente", "Cidade/UF",
            "Itens", "Total", "Status", "NF-e", "Data");
    private final JTable tabela = Estilo.tabela(modelo);
    private final JComboBox<Object> filtro = new JComboBox<>();
    private final JCheckBox autoNf = new JCheckBox("Emitir NF-e automaticamente");
    private final JToggleButton autoSim = new JToggleButton("Simulação automática: OFF");
    private final Timer timer;

    public PedidosPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Estilo.FUNDO);

        autoNf.setOpaque(false);
        autoNf.setFont(Estilo.NEGRITO);
        autoNf.addActionListener(e -> {
            repo.setFaturamentoAutomatico(autoNf.isSelected());
            repo.log(Evento.Tipo.FISCAL, "Faturamento automático " + (autoNf.isSelected() ? "ativado." : "desativado."));
            repo.alterado();
        });

        timer = new Timer(3000, e -> {
            Contexto.PEDIDOS.simularPedido();
            repo.alterado();
        });
        autoSim.setFont(Estilo.NORMAL);
        autoSim.addActionListener(e -> {
            if (autoSim.isSelected()) { timer.start(); autoSim.setText("Simulação automática: ON (1 pedido/3s)"); }
            else { timer.stop(); autoSim.setText("Simulação automática: OFF"); }
        });

        filtro.addItem("Todos os canais");
        for (Canal c : Canal.values()) filtro.addItem(c);
        filtro.addActionListener(e -> atualizar());

        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        topo.add(Estilo.barra(
                Estilo.botaoPrimario("Simular pedido", () -> {
                    if (Contexto.PEDIDOS.simularPedido() == null)
                        JOptionPane.showMessageDialog(this, "Nenhum anúncio sincronizado com estoque para gerar pedido.");
                    repo.alterado();
                }),
                autoSim,
                Estilo.botao("Faturar (NF-e)", () -> {
                    Pedido p = selecionado();
                    if (p == null) return;
                    if (p.getStatus() == Pedido.Status.CANCELADO) { JOptionPane.showMessageDialog(this, "Pedido cancelado."); return; }
                    NotaFiscal nf = Contexto.PEDIDOS.faturar(p);
                    repo.alterado();
                    if (nf != null) JOptionPane.showMessageDialog(this, "NF-e " + nf.getNumero() + " autorizada.\nChave: "
                            + nf.getChave());
                }),
                Estilo.botao("Marcar enviado", () -> {
                    Pedido p = selecionado();
                    if (p == null) return;
                    if (p.getStatus() != Pedido.Status.FATURADO) {
                        JOptionPane.showMessageDialog(this, "Somente pedidos FATURADOS podem ser enviados.");
                        return;
                    }
                    Contexto.PEDIDOS.marcarEnviado(p);
                    repo.alterado();
                }),
                Estilo.botao("Cancelar pedido", () -> {
                    Pedido p = selecionado();
                    if (p == null || p.getStatus() == Pedido.Status.CANCELADO) return;
                    if (JOptionPane.showConfirmDialog(this, "Cancelar pedido #" + p.getNumero()
                            + "? O estoque volta para todos os canais e a NF-e é cancelada.", "Confirmar",
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        Contexto.PEDIDOS.cancelar(p);
                        repo.alterado();
                    }
                }),
                Estilo.botao("Detalhes", this::detalhes)
        ), BorderLayout.WEST);
        topo.add(Estilo.barra(autoNf, new JLabel("  Canal:"), filtro), BorderLayout.EAST);
        add(topo, BorderLayout.NORTH);

        Estilo.larguras(tabela, 60, 110, 150, 140, 140, 50, 100, 100, 60, 140);
        tabela.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) detalhes(); }
        });
        add(Estilo.rolagem(tabela), BorderLayout.CENTER);

        JLabel dica = new JLabel("Fluxo automático: pedido recebido do marketplace → baixa no estoque central → "
                + "estoque propagado a TODOS os canais → NF-e emitida. Pedidos sem estoque são recusados (sem overselling).");
        dica.setForeground(Estilo.TEXTO_SUAVE);
        add(dica, BorderLayout.SOUTH);
        atualizar();
    }

    private Pedido selecionado() {
        int r = tabela.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um pedido.");
            return null;
        }
        return repo.pedido((Integer) modelo.getValueAt(r, 0));
    }

    private void detalhes() {
        Pedido p = selecionado();
        if (p == null) return;
        StringBuilder s = new StringBuilder();
        s.append("Pedido #").append(p.getNumero()).append("  -  ").append(p.getCanal()).append(" (").append(p.getCodigoCanal()).append(")\n");
        s.append("Data: ").append(Estilo.DATA_HORA.format(p.getData())).append("\n");
        s.append("Cliente: ").append(p.getCliente()).append("  CPF: ").append(p.getCpf()).append("\n");
        s.append("Entrega: ").append(p.getCidadeUf()).append("\n");
        s.append("Status: ").append(p.getStatus()).append(p.getNumeroNota() != null ? "   NF-e nº " + p.getNumeroNota() : "").append("\n\n");
        s.append(String.format("%-10s %-40s %5s %12s %12s%n", "SKU", "Produto", "Qtd", "Unit.", "Total"));
        for (ItemPedido i : p.getItens())
            s.append(String.format("%-10s %-40s %5d %12s %12s%n", i.getSku(),
                    i.getDescricao().length() > 40 ? i.getDescricao().substring(0, 40) : i.getDescricao(),
                    i.getQuantidade(), Estilo.moeda(i.getPrecoUnitario()), Estilo.moeda(i.getTotal())));
        s.append("\nTOTAL: ").append(Estilo.moeda(p.getTotal())).append("\n");
        s.append("Comissão estimada do canal: ").append(Estilo.moeda(p.getTotal() * p.getCanal().getComissao() + p.getCanal().getTaxaFixa())).append("\n");
        Estilo.mostrarTexto(this, "Pedido #" + p.getNumero(), s.toString());
    }

    @Override
    public void atualizar() {
        autoNf.setSelected(repo.isFaturamentoAutomatico());
        Object f = filtro.getSelectedItem();
        List<Pedido> lista = new ArrayList<>(repo.getPedidos());
        lista.sort((a, b) -> Integer.compare(b.getNumero(), a.getNumero()));
        List<Object[]> dados = new ArrayList<>();
        for (Pedido p : lista) {
            if (f instanceof Canal && p.getCanal() != f) continue;
            dados.add(new Object[]{p.getNumero(), p.getCanal().getNome(), p.getCodigoCanal(), p.getCliente(), p.getCidadeUf(),
                    p.getQuantidadeItens(), Estilo.moeda(p.getTotal()), p.getStatus().name(),
                    p.getNumeroNota() == null ? "-" : String.valueOf(p.getNumeroNota()), Estilo.DATA_HORA.format(p.getData())});
        }
        Estilo.recarregar(tabela, modelo, dados);
    }
}
