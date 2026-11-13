package br.com.cataloghub.ui;

import br.com.cataloghub.service.DadosDemo;
import br.com.cataloghub.service.Repositorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

    private final List<Atualizavel> telas = new ArrayList<>();

    public MainFrame() {
        super("CatalogHub - Gestão Multicanal de Catálogo e Marketplaces");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1360, 820);
        setMinimumSize(new Dimension(1100, 650));
        setLocationRelativeTo(null);

        Repositorio repo = Contexto.REPO;

        JPanel cabecalho = new JPanel(new BorderLayout());
        cabecalho.setBackground(Estilo.PRIMARIA);
        cabecalho.setBorder(new EmptyBorder(10, 16, 10, 16));
        JLabel titulo = new JLabel("CatalogHub");
        titulo.setFont(Estilo.TITULO);
        titulo.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Cadastro único  •  Estoque sincronizado  •  NF-e automática  •  IA  —  "
                + "Mercado Livre, Magalu, Shopee, TikTok Shop e Loja Própria");
        sub.setForeground(new Color(220, 245, 235));
        sub.setFont(Estilo.NORMAL);
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(titulo);
        textos.add(sub);
        cabecalho.add(textos, BorderLayout.WEST);
        JButton reset = Estilo.botao("Resetar demonstração", () -> {
            if (JOptionPane.showConfirmDialog(this, "Apagar todos os dados e recarregar o exemplo?", "Resetar",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                repo.resetar();
                DadosDemo.popular(Contexto.SYNC, Contexto.PEDIDOS);
            }
        });
        JPanel dir = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        dir.setOpaque(false);
        dir.add(reset);
        cabecalho.add(dir, BorderLayout.EAST);

        JTabbedPane abas = new JTabbedPane();
        abas.setFont(Estilo.NEGRITO);
        adicionar(abas, "Dashboard", new DashboardPanel());
        adicionar(abas, "Catálogo central", new CatalogoPanel());
        adicionar(abas, "Canais & Anúncios", new CanaisPanel());
        adicionar(abas, "Pedidos", new PedidosPanel());
        adicionar(abas, "Notas fiscais", new NotasPanel());
        adicionar(abas, "Log de integrações", new LogPanel());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(cabecalho, BorderLayout.NORTH);
        getContentPane().add(abas, BorderLayout.CENTER);

        repo.addListener(() -> {
            for (Atualizavel t : telas) t.atualizar();
        });
    }

    private void adicionar(JTabbedPane abas, String nome, JPanel tela) {
        abas.addTab(nome, tela);
        telas.add((Atualizavel) tela);
    }
}
