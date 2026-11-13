package br.com.cataloghub.ui;

import br.com.cataloghub.model.NotaFiscal;
import br.com.cataloghub.service.FiscalService;
import br.com.cataloghub.service.Repositorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Notas fiscais emitidas automaticamente. */
public class NotasPanel extends JPanel implements Atualizavel {

    private final Repositorio repo = Contexto.REPO;
    private final DefaultTableModel modelo = Estilo.modelo("NF-e", "Série", "Pedido", "Canal", "Destinatário",
            "Valor", "Tributos aprox.", "Status", "Emissão", "Chave de acesso");
    private final JTable tabela = Estilo.tabela(modelo);

    public NotasPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Estilo.FUNDO);
        add(Estilo.barra(
                Estilo.botaoPrimario("Ver DANFE", () -> {
                    NotaFiscal n = selecionada();
                    if (n != null) Estilo.mostrarTexto(this, "DANFE - NF-e " + n.getNumero(), FiscalService.gerarDanfe(n));
                }),
                Estilo.botao("Ver XML", () -> {
                    NotaFiscal n = selecionada();
                    if (n != null) Estilo.mostrarTexto(this, "XML - NF-e " + n.getNumero(), FiscalService.gerarXml(n));
                }),
                Estilo.botao("Exportar XML", this::exportar),
                Estilo.botao("Exportar todas (pasta)", this::exportarTodas)
        ), BorderLayout.NORTH);
        Estilo.larguras(tabela, 60, 50, 60, 110, 140, 100, 100, 100, 140, 380);
        tabela.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    NotaFiscal n = selecionada();
                    if (n != null) Estilo.mostrarTexto(NotasPanel.this, "DANFE - NF-e " + n.getNumero(), FiscalService.gerarDanfe(n));
                }
            }
        });
        add(Estilo.rolagem(tabela), BorderLayout.CENTER);
        JLabel aviso = new JLabel("Ambiente de HOMOLOGAÇÃO simulado - notas sem valor fiscal, geradas para demonstração acadêmica.");
        aviso.setForeground(Estilo.TEXTO_SUAVE);
        add(aviso, BorderLayout.SOUTH);
        atualizar();
    }

    private NotaFiscal selecionada() {
        int r = tabela.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma nota.");
            return null;
        }
        return repo.nota((Integer) modelo.getValueAt(r, 0));
    }

    private void exportar() {
        NotaFiscal n = selecionada();
        if (n == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(n.getChave() + "-nfe.xml"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            gravar(fc.getSelectedFile(), FiscalService.gerarXml(n));
            JOptionPane.showMessageDialog(this, "XML salvo em " + fc.getSelectedFile().getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erro: " + e.getMessage());
        }
    }

    private void exportarTodas() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int n = 0;
        try {
            for (NotaFiscal nf : repo.getNotas()) {
                gravar(new File(fc.getSelectedFile(), nf.getChave() + "-nfe.xml"), FiscalService.gerarXml(nf));
                n++;
            }
            JOptionPane.showMessageDialog(this, n + " XML(s) exportado(s).");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erro: " + e.getMessage());
        }
    }

    private static void gravar(File f, String conteudo) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(conteudo);
        }
    }

    @Override
    public void atualizar() {
        List<Object[]> dados = new ArrayList<>();
        for (int i = repo.getNotas().size() - 1; i >= 0; i--) {
            NotaFiscal n = repo.getNotas().get(i);
            dados.add(new Object[]{n.getNumero(), n.getSerie(), n.getPedidoNumero(), n.getCanal().getNome(), n.getCliente(),
                    Estilo.moeda(n.getValorProdutos()), Estilo.moeda(n.getTributosAprox()), n.getStatus().name(),
                    Estilo.DATA_HORA.format(n.getDataEmissao()), FiscalService.chaveFormatada(n.getChave())});
        }
        Estilo.recarregar(tabela, modelo, dados);
    }
}
