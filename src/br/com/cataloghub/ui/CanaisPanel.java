package br.com.cataloghub.ui;

import br.com.cataloghub.model.*;
import br.com.cataloghub.service.MockMarketplaceConnector;
import br.com.cataloghub.service.Repositorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Integrações: status de conexão por canal e anúncios sincronizados. */
public class CanaisPanel extends JPanel implements Atualizavel {

    private final Repositorio repo = Contexto.REPO;
    private final Map<Canal, JCheckBox> conexoes = new EnumMap<>(Canal.class);
    private final DefaultTableModel modelo = Estilo.modelo("Canal", "ID no canal", "SKU", "Título", "Preço",
            "Estoque no canal", "Estoque central", "Status", "Última sync", "Retorno da API");
    private final JTable tabela = Estilo.tabela(modelo);
    private final JComboBox<Object> filtro = new JComboBox<>();
    private final JComboBox<String> filtroStatus = new JComboBox<>(new String[]{"Todos", "SINCRONIZADO", "PENDENTE", "ERRO", "PAUSADO"});
    private final List<Anuncio> linhas = new ArrayList<>();

    public CanaisPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Estilo.FUNDO);

        JPanel conexao = new JPanel(new GridLayout(1, Canal.values().length, 8, 0));
        conexao.setBackground(Color.WHITE);
        conexao.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(220, 224, 228)),
                "Conexões (API dos marketplaces - simuladas)", TitledBorder.LEFT, TitledBorder.TOP, Estilo.NEGRITO));
        for (Canal c : Canal.values()) {
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(Color.WHITE);
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 5, 0, 0, new Color(c.getCor())),
                    new EmptyBorder(4, 8, 4, 4)));
            JLabel nome = new JLabel(c.getNome());
            nome.setFont(Estilo.NEGRITO);
            JLabel info = new JLabel("<html>Comissão " + Estilo.pct(c.getComissao()) + " + " + Estilo.moeda(c.getTaxaFixa())
                    + "<br>Título máx. " + c.getMaxTitulo() + " caracteres</html>");
            info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            info.setForeground(Estilo.TEXTO_SUAVE);
            JCheckBox cb = new JCheckBox("Conectado", repo.conectado(c));
            cb.setBackground(Color.WHITE);
            cb.addActionListener(e -> alternarConexao(c, cb.isSelected()));
            conexoes.put(c, cb);
            p.add(nome, BorderLayout.NORTH);
            p.add(info, BorderLayout.CENTER);
            p.add(cb, BorderLayout.SOUTH);
            conexao.add(p);
        }

        filtro.addItem("Todos os canais");
        for (Canal c : Canal.values()) filtro.addItem(c);
        filtro.addActionListener(e -> atualizar());
        filtroStatus.addActionListener(e -> atualizar());

        JSpinner falha = new JSpinner(new SpinnerNumberModel((int) (MockMarketplaceConnector.taxaFalha * 100), 0, 100, 5));
        falha.addChangeListener(e -> MockMarketplaceConnector.taxaFalha = ((Integer) falha.getValue()) / 100.0);
        falha.setToolTipText("Simula instabilidade das APIs dos marketplaces para demonstrar o reprocessamento");

        JPanel acoes = new JPanel(new BorderLayout());
        acoes.setOpaque(false);
        acoes.add(Estilo.barra(
                Estilo.botaoPrimario("Sincronizar tudo", () -> {
                    int[] r = Contexto.SYNC.sincronizarTudo();
                    repo.alterado();
                    JOptionPane.showMessageDialog(this, r[0] + " ok, " + r[1] + " com erro/pendente.");
                }),
                Estilo.botao("Reprocessar erros", () -> {
                    int n = Contexto.SYNC.reprocessarErros();
                    repo.alterado();
                    JOptionPane.showMessageDialog(this, n + " anúncio(s) recuperado(s).");
                }),
                Estilo.botao("Pausar / Reativar", () -> {
                    Anuncio a = selecionado();
                    if (a != null) { Contexto.SYNC.pausarOuAtivar(a); repo.alterado(); }
                }),
                Estilo.botao("Remover anúncio", () -> {
                    Anuncio a = selecionado();
                    if (a != null && JOptionPane.showConfirmDialog(this, "Remover anúncio de " + a.getSku() + " em " + a.getCanal() + "?",
                            "Confirmar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        Contexto.SYNC.despublicar(a);
                        repo.alterado();
                    }
                })
        ), BorderLayout.WEST);
        acoes.add(Estilo.barra(new JLabel("Canal:"), filtro, new JLabel("Status:"), filtroStatus,
                new JLabel("  Falha simulada da API (%):"), falha), BorderLayout.EAST);

        JPanel norte = new JPanel(new BorderLayout(0, 6));
        norte.setOpaque(false);
        norte.add(conexao, BorderLayout.NORTH);
        norte.add(acoes, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);

        Estilo.larguras(tabela, 100, 120, 80, 330, 85, 90, 90, 110, 140, 260);
        add(Estilo.rolagem(tabela), BorderLayout.CENTER);
        atualizar();
    }

    private void alternarConexao(Canal c, boolean conectar) {
        repo.setConectado(c, conectar);
        if (conectar) {
            repo.log(Evento.Tipo.SYNC, c + " reconectado. Reprocessando fila pendente...");
            Contexto.SYNC.reprocessarErros();
        } else {
            repo.log(Evento.Tipo.SYNC, c + " desconectado. Alterações ficarão pendentes até a reconexão.");
            for (Anuncio a : repo.getAnuncios()) {
                if (a.getCanal() == c && a.getStatus() != Anuncio.Status.PAUSADO) {
                    a.setStatus(Anuncio.Status.PENDENTE);
                    a.setMensagem("Canal desconectado - aguardando reconexão");
                }
            }
        }
        repo.alterado();
    }

    private Anuncio selecionado() {
        int r = tabela.getSelectedRow();
        if (r < 0 || r >= linhas.size()) {
            JOptionPane.showMessageDialog(this, "Selecione um anúncio.");
            return null;
        }
        return linhas.get(r);
    }

    @Override
    public void atualizar() {
        for (Map.Entry<Canal, JCheckBox> e : conexoes.entrySet()) e.getValue().setSelected(repo.conectado(e.getKey()));
        Object f = filtro.getSelectedItem();
        String st = (String) filtroStatus.getSelectedItem();
        linhas.clear();
        List<Object[]> dados = new ArrayList<>();
        List<Anuncio> ordenados = new ArrayList<>(repo.getAnuncios());
        ordenados.sort(Comparator.comparing((Anuncio a) -> a.getCanal().ordinal()).thenComparing(Anuncio::getSku));
        for (Anuncio a : ordenados) {
            if (f instanceof Canal && a.getCanal() != f) continue;
            if (!"Todos".equals(st) && !a.getStatus().name().equals(st)) continue;
            Produto p = repo.produto(a.getSku());
            linhas.add(a);
            dados.add(new Object[]{a.getCanal().getNome(), a.getIdExterno() == null ? "-" : a.getIdExterno(), a.getSku(),
                    a.getTitulo(), Estilo.moeda(a.getPreco()), a.getEstoquePublicado(), p == null ? "-" : p.getEstoque(),
                    a.getStatus().name(), a.getUltimaSync() == null ? "-" : Estilo.DATA_HORA.format(a.getUltimaSync()),
                    a.getMensagem()});
        }
        Estilo.recarregar(tabela, modelo, dados);
    }
}
