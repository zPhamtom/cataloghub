package br.com.cataloghub.ui;

import br.com.cataloghub.model.*;
import br.com.cataloghub.service.IAService;
import br.com.cataloghub.service.Repositorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/** Cadastro centralizado: um cadastro, publicado em todos os canais. */
public class CatalogoPanel extends JPanel implements Atualizavel {

    private final Repositorio repo = Contexto.REPO;
    private final DefaultTableModel modelo = Estilo.modelo("SKU", "Produto", "Marca", "Categoria", "NCM",
            "Preço base", "Custo", "Estoque", "Situação", "Canais");
    private final JTable tabela = Estilo.tabela(modelo);
    private final DefaultTableModel modeloCanais = Estilo.modelo("Canal", "Publicado", "Título no canal",
            "Preço anúncio", "Margem real", "Preço sugerido IA", "Status");
    private final JTable tabelaCanais = Estilo.tabela(modeloCanais);
    private final JTextField busca = new JTextField(22);
    private final JLabel detalhe = new JLabel(" ");

    public CatalogoPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Estilo.FUNDO);

        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        topo.add(Estilo.barra(
                Estilo.botaoPrimario("+ Novo produto", () -> editar(null)),
                Estilo.botao("Editar", () -> { Produto p = selecionado(); if (p != null) editar(p); }),
                Estilo.botao("Excluir", this::excluir),
                Estilo.botao("Ajustar estoque", this::ajustarEstoque),
                Estilo.botao("Publicar nos canais", this::publicar),
                Estilo.botao("Importar CSV", this::importarCsv),
                Estilo.botao("Exportar CSV", this::exportarCsv)
        ), BorderLayout.WEST);
        busca.setFont(Estilo.NORMAL);
        busca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { atualizar(); }
            public void removeUpdate(DocumentEvent e) { atualizar(); }
            public void changedUpdate(DocumentEvent e) { atualizar(); }
        });
        topo.add(Estilo.barra(new JLabel("Buscar:"), busca), BorderLayout.EAST);
        add(topo, BorderLayout.NORTH);

        Estilo.larguras(tabela, 80, 260, 90, 150, 80, 90, 80, 60, 90, 60);
        tabela.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) atualizarDetalhe(); });
        tabela.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) { Produto p = selecionado(); if (p != null) editar(p); }
            }
        });

        JPanel baixo = new JPanel(new BorderLayout(0, 6));
        baixo.setOpaque(false);
        detalhe.setFont(new Font("Segoe UI", Font.BOLD, 14));
        detalhe.setForeground(Estilo.PRIMARIA_ESCURA);
        baixo.add(detalhe, BorderLayout.NORTH);
        Estilo.larguras(tabelaCanais, 110, 70, 330, 100, 90, 120, 110);
        baixo.add(Estilo.rolagem(tabelaCanais), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Estilo.rolagem(tabela), baixo);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        atualizar();
    }

    private Produto selecionado() {
        int r = tabela.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um produto na tabela.");
            return null;
        }
        return repo.produto(String.valueOf(modelo.getValueAt(r, 0)));
    }

    @Override
    public void atualizar() {
        String f = busca.getText().trim().toLowerCase();
        List<Object[]> linhas = new ArrayList<>();
        for (Produto p : repo.getProdutos()) {
            if (!f.isEmpty() && !(p.getSku() + " " + p.getNome() + " " + p.getMarca() + " " + p.getCategoria()).toLowerCase().contains(f))
                continue;
            String sit = p.getEstoque() == 0 ? "SEM ESTOQUE" : p.getEstoque() <= p.getEstoqueMinimo() ? "BAIXO" : "OK";
            linhas.add(new Object[]{p.getSku(), p.getNome(), p.getMarca(), p.getCategoria(), p.getNcm(),
                    Estilo.moeda(p.getPreco()), Estilo.moeda(p.getCusto()), p.getEstoque(), sit,
                    repo.anunciosDoProduto(p.getSku()).size() + "/" + Canal.values().length});
        }
        Estilo.recarregar(tabela, modelo, linhas);
        atualizarDetalhe();
    }

    private void atualizarDetalhe() {
        int r = tabela.getSelectedRow();
        modeloCanais.setRowCount(0);
        if (r < 0) {
            detalhe.setText("Selecione um produto para ver anúncios, margens e preço sugerido pela IA em cada canal.");
            return;
        }
        Produto p = repo.produto(String.valueOf(modelo.getValueAt(r, 0)));
        if (p == null) return;
        int dias = IAService.diasAteRuptura(p, repo.getPedidos());
        detalhe.setText(p.getSku() + " - " + p.getNome() + "   |   Estoque: " + p.getEstoque()
                + "   |   Previsão IA: " + (dias < 0 ? "sem vendas nos últimos 7 dias" : "esgota em ~" + dias + " dia(s)"));
        for (Canal c : Canal.values()) {
            Anuncio a = repo.anuncio(p.getSku(), c);
            double sug = IAService.sugerirPreco(p, c, IAService.MARGEM_ALVO);
            if (a == null) {
                modeloCanais.addRow(new Object[]{c.getNome(), "Não", "-", "-", "-", Estilo.moeda(sug), "-"});
            } else {
                modeloCanais.addRow(new Object[]{c.getNome(), "Sim", a.getTitulo(), Estilo.moeda(a.getPreco()),
                        Estilo.pct(IAService.margem(p, c, a.getPreco())), Estilo.moeda(sug), a.getStatus().name()});
            }
        }
    }

    private void excluir() {
        Produto p = selecionado();
        if (p == null) return;
        if (JOptionPane.showConfirmDialog(this, "Excluir " + p.getSku() + " e remover de todos os canais?",
                "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        for (Anuncio a : repo.anunciosDoProduto(p.getSku())) Contexto.SYNC.despublicar(a);
        repo.getProdutos().remove(p);
        repo.log(Evento.Tipo.CATALOGO, "Produto " + p.getSku() + " excluído.");
        repo.alterado();
    }

    private void ajustarEstoque() {
        Produto p = selecionado();
        if (p == null) return;
        String s = JOptionPane.showInputDialog(this, "Novo estoque central de " + p.getSku()
                + " (atual: " + p.getEstoque() + ").\nSerá propagado para todos os canais:", p.getEstoque());
        if (s == null) return;
        try {
            int novo = Integer.parseInt(s.trim());
            if (novo < 0) throw new NumberFormatException();
            int antigo = p.getEstoque();
            p.setEstoque(novo);
            Contexto.SYNC.sincronizarProduto(p);
            repo.log(Evento.Tipo.CATALOGO, "Estoque de " + p.getSku() + " ajustado de " + antigo + " para " + novo + " e sincronizado.");
            repo.alterado();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Valor inválido.");
        }
    }

    private void publicar() {
        Produto p = selecionado();
        if (p == null) return;
        JPanel painel = new JPanel(new GridLayout(0, 1));
        Map<Canal, JCheckBox> checks = new EnumMap<>(Canal.class);
        painel.add(new JLabel("Publicar/atualizar " + p.getSku() + " nos canais:"));
        for (Canal c : Canal.values()) {
            JCheckBox cb = new JCheckBox(c.getNome() + "  (comissão " + Estilo.pct(c.getComissao()) + ")", true);
            checks.put(c, cb);
            painel.add(cb);
        }
        JCheckBox precoIA = new JCheckBox("Usar preço sugerido pela IA em cada canal (margem alvo "
                + Estilo.pct(IAService.MARGEM_ALVO) + ")", true);
        painel.add(precoIA);
        if (JOptionPane.showConfirmDialog(this, painel, "Publicar nos canais", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
            return;
        List<Canal> sel = new ArrayList<>();
        for (Map.Entry<Canal, JCheckBox> e : checks.entrySet()) if (e.getValue().isSelected()) sel.add(e.getKey());
        int ok = Contexto.SYNC.publicar(p, sel, precoIA.isSelected());
        repo.alterado();
        JOptionPane.showMessageDialog(this, "Publicado com sucesso em " + ok + " de " + sel.size() + " canal(is)."
                + (ok < sel.size() ? "\nFalhas ficam na fila de reprocessamento (aba Canais)." : ""));
    }

    private void editar(Produto existente) {
        ProdutoDialog d = new ProdutoDialog(SwingUtilities.getWindowAncestor(this), existente);
        d.setVisible(true);
        if (d.salvo) repo.alterado();
    }

    // ---------------- CSV ----------------
    private void exportarCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("catalogo.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            w.println("sku;nome;marca;preco;custo;estoque;categoria;ncm;ean");
            for (Produto p : repo.getProdutos()) {
                w.println(String.join(";", p.getSku(), p.getNome(), p.getMarca(),
                        String.format(Locale.US, "%.2f", p.getPreco()), String.format(Locale.US, "%.2f", p.getCusto()),
                        String.valueOf(p.getEstoque()), p.getCategoria(), p.getNcm(), p.getEan()));
            }
            JOptionPane.showMessageDialog(this, "Catálogo exportado: " + fc.getSelectedFile().getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erro: " + e.getMessage());
        }
    }

    private void importarCsv() {
        JOptionPane.showMessageDialog(this, "Formato esperado (separador ';', 1ª linha = cabeçalho):\n"
                + "sku;nome;marca;preco;custo;estoque\n\nCategoria, NCM e descrição são preenchidos pela IA.");
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int novos = 0, atualizados = 0, erros = 0;
        List<Produto> importados = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            String linha = r.readLine(); // cabeçalho
            while ((linha = r.readLine()) != null) {
                if (linha.trim().isEmpty()) continue;
                String[] c = linha.split(";");
                try {
                    String sku = c[0].trim().replace("﻿", "");
                    Produto p = repo.produto(sku);
                    boolean novo = p == null;
                    if (novo) p = new Produto();
                    p.setSku(sku);
                    p.setNome(c[1].trim());
                    p.setMarca(c[2].trim());
                    p.setPreco(num(c[3]));
                    p.setCusto(num(c[4]));
                    p.setEstoque(Integer.parseInt(c[5].trim()));
                    String[] cn = IAService.sugerirCategoriaNcm(p.getNome());
                    if (p.getCategoria() == null || p.getCategoria().isEmpty()) p.setCategoria(cn[0]);
                    if (p.getNcm() == null || p.getNcm().isEmpty()) p.setNcm(cn[1]);
                    if (p.getEan() == null) p.setEan("");
                    if (p.getDescricao() == null || p.getDescricao().isEmpty()) p.setDescricao(IAService.gerarDescricao(p));
                    if (novo) { repo.getProdutos().add(p); novos++; } else atualizados++;
                    importados.add(p);
                } catch (Exception ex) {
                    erros++;
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erro: " + e.getMessage());
            return;
        }
        repo.log(Evento.Tipo.CATALOGO, "Importação CSV: " + novos + " novos, " + atualizados + " atualizados, " + erros + " erro(s).");
        if (!importados.isEmpty() && JOptionPane.showConfirmDialog(this,
                novos + " novo(s), " + atualizados + " atualizado(s), " + erros + " linha(s) com erro.\n\nPublicar/sincronizar em TODOS os canais agora?",
                "Importação concluída", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            for (Produto p : importados) Contexto.SYNC.publicar(p, Arrays.asList(Canal.values()), true);
        }
        repo.alterado();
    }

    static double num(String s) { return Double.parseDouble(s.trim().replace("R$", "").replace(" ", "").replace(",", ".")); }

    // ---------------- Diálogo de cadastro ----------------
    static class ProdutoDialog extends JDialog {
        boolean salvo;
        private final Produto existente;
        private final JTextField sku = new JTextField(), nome = new JTextField(), marca = new JTextField(),
                categoria = new JTextField(), ncm = new JTextField(), ean = new JTextField(), preco = new JTextField(),
                custo = new JTextField(), estoque = new JTextField(), estoqueMin = new JTextField(), peso = new JTextField();
        private final JTextArea descricao = new JTextArea(8, 40);
        private final Map<Canal, JCheckBox> canais = new EnumMap<>(Canal.class);
        private final JCheckBox precoIA = new JCheckBox("Preço por canal sugerido pela IA", true);

        ProdutoDialog(Window owner, Produto p) {
            super(owner, p == null ? "Novo produto" : "Editar " + p.getSku(), ModalityType.APPLICATION_MODAL);
            this.existente = p;
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new EmptyBorder(12, 12, 12, 12));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 4, 4, 4);
            g.fill = GridBagConstraints.HORIZONTAL;
            Object[][] campos = {{"SKU*", sku}, {"Nome do produto*", nome}, {"Marca", marca}, {"Categoria", categoria},
                    {"NCM", ncm}, {"EAN/GTIN", ean}, {"Preço base (R$)*", preco}, {"Custo (R$)*", custo},
                    {"Estoque*", estoque}, {"Estoque mínimo", estoqueMin}, {"Peso (kg)", peso}};
            int row = 0;
            for (Object[] c : campos) {
                g.gridx = 0; g.gridy = row; g.weightx = 0;
                form.add(new JLabel((String) c[0]), g);
                g.gridx = 1; g.weightx = 1;
                ((JTextField) c[1]).setColumns(28);
                form.add((JTextField) c[1], g);
                row++;
            }
            g.gridx = 0; g.gridy = row; g.weightx = 0; g.anchor = GridBagConstraints.NORTH;
            form.add(new JLabel("Descrição"), g);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.BOTH; g.weighty = 1;
            descricao.setLineWrap(true);
            descricao.setWrapStyleWord(true);
            descricao.setFont(Estilo.NORMAL);
            form.add(new JScrollPane(descricao), g);

            JPanel lado = new JPanel();
            lado.setLayout(new BoxLayout(lado, BoxLayout.Y_AXIS));
            lado.setBorder(new EmptyBorder(12, 4, 12, 12));
            JButton ia = Estilo.botaoPrimario("Preencher com IA", this::preencherIA);
            ia.setToolTipText("Sugere categoria, NCM, descrição e preço a partir do nome e custo");
            lado.add(ia);
            lado.add(Box.createVerticalStrut(14));
            JLabel l = new JLabel("Publicar em:");
            l.setFont(Estilo.NEGRITO);
            lado.add(l);
            for (Canal c : Canal.values()) {
                boolean marcado = p == null || Contexto.REPO.anuncio(p.getSku(), c) != null;
                JCheckBox cb = new JCheckBox(c.getNome(), marcado);
                canais.put(c, cb);
                lado.add(cb);
            }
            lado.add(Box.createVerticalStrut(8));
            lado.add(precoIA);
            lado.add(Box.createVerticalGlue());

            JPanel botoes = Estilo.barra(Estilo.botaoPrimario("Salvar e sincronizar", this::salvar),
                    Estilo.botao("Cancelar", this::dispose));
            ((FlowLayout) botoes.getLayout()).setAlignment(FlowLayout.RIGHT);

            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(form, BorderLayout.CENTER);
            getContentPane().add(lado, BorderLayout.EAST);
            getContentPane().add(botoes, BorderLayout.SOUTH);

            if (p != null) {
                sku.setText(p.getSku());
                sku.setEditable(false);
                nome.setText(p.getNome());
                marca.setText(p.getMarca());
                categoria.setText(p.getCategoria());
                ncm.setText(p.getNcm());
                ean.setText(p.getEan());
                preco.setText(String.format(Locale.US, "%.2f", p.getPreco()));
                custo.setText(String.format(Locale.US, "%.2f", p.getCusto()));
                estoque.setText(String.valueOf(p.getEstoque()));
                estoqueMin.setText(String.valueOf(p.getEstoqueMinimo()));
                peso.setText(String.format(Locale.US, "%.2f", p.getPesoKg()));
                descricao.setText(p.getDescricao());
            } else {
                estoqueMin.setText("5");
            }
            pack();
            setLocationRelativeTo(owner);
        }

        private void preencherIA() {
            if (nome.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Informe ao menos o nome do produto.");
                return;
            }
            String[] cn = IAService.sugerirCategoriaNcm(nome.getText());
            categoria.setText(cn[0]);
            ncm.setText(cn[1]);
            Produto tmp = montar(new Produto(), false);
            descricao.setText(IAService.gerarDescricao(tmp));
            StringBuilder msg = new StringBuilder("IA preencheu categoria, NCM e descrição.");
            try {
                double c = num(custo.getText());
                tmp.setCusto(c);
                double sug = IAService.sugerirPreco(tmp, Canal.LOJA_PROPRIA, IAService.MARGEM_ALVO);
                if (preco.getText().trim().isEmpty()) preco.setText(String.format(Locale.US, "%.2f", sug));
                msg.append("\n\nPreços sugeridos (margem ").append(Estilo.pct(IAService.MARGEM_ALVO)).append("):");
                for (Canal ch : Canal.values())
                    msg.append("\n  ").append(ch.getNome()).append(": ").append(Estilo.moeda(IAService.sugerirPreco(tmp, ch, IAService.MARGEM_ALVO)));
            } catch (Exception ignored) {
                msg.append("\n\nInforme o custo para receber sugestão de preço.");
            }
            Contexto.REPO.log(Evento.Tipo.IA, "IA sugeriu cadastro para '" + nome.getText().trim() + "' (" + cn[0] + ", NCM " + cn[1] + ")");
            JOptionPane.showMessageDialog(this, msg.toString(), "Assistente IA", JOptionPane.INFORMATION_MESSAGE);
        }

        private Produto montar(Produto p, boolean numeros) {
            p.setSku(sku.getText().trim().toUpperCase());
            p.setNome(nome.getText().trim());
            p.setMarca(marca.getText().trim());
            p.setCategoria(categoria.getText().trim());
            p.setNcm(ncm.getText().trim());
            p.setEan(ean.getText().trim());
            p.setDescricao(descricao.getText());
            if (numeros) {
                p.setPreco(num(preco.getText()));
                p.setCusto(num(custo.getText()));
                p.setEstoque(Integer.parseInt(estoque.getText().trim()));
                p.setEstoqueMinimo(estoqueMin.getText().trim().isEmpty() ? 0 : Integer.parseInt(estoqueMin.getText().trim()));
                p.setPesoKg(peso.getText().trim().isEmpty() ? 0 : num(peso.getText()));
            }
            return p;
        }

        private void salvar() {
            Repositorio repo = Contexto.REPO;
            if (sku.getText().trim().isEmpty() || nome.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "SKU e nome são obrigatórios.");
                return;
            }
            if (existente == null && repo.produto(sku.getText().trim()) != null) {
                JOptionPane.showMessageDialog(this, "Já existe um produto com este SKU.");
                return;
            }
            Produto p;
            try {
                p = montar(existente != null ? existente : new Produto(), true);
                if (p.getPreco() <= 0 || p.getCusto() < 0 || p.getEstoque() < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Verifique preço, custo, estoque e peso (valores numéricos válidos).");
                return;
            }
            if (p.getCategoria().isEmpty() || p.getNcm().isEmpty()) {
                String[] cn = IAService.sugerirCategoriaNcm(p.getNome());
                if (p.getCategoria().isEmpty()) p.setCategoria(cn[0]);
                if (p.getNcm().isEmpty()) p.setNcm(cn[1]);
            }
            if (existente == null) repo.getProdutos().add(p);
            repo.log(Evento.Tipo.CATALOGO, "Produto " + p.getSku() + (existente == null ? " cadastrado." : " atualizado."));

            List<Canal> publicar = new ArrayList<>();
            for (Map.Entry<Canal, JCheckBox> e : canais.entrySet()) {
                Anuncio a = repo.anuncio(p.getSku(), e.getKey());
                if (e.getValue().isSelected()) publicar.add(e.getKey());
                else if (a != null) Contexto.SYNC.despublicar(a);
            }
            if (!publicar.isEmpty()) Contexto.SYNC.publicar(p, publicar, precoIA.isSelected());
            salvo = true;
            dispose();
        }
    }
}
