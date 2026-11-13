package br.com.cataloghub.ui;

import br.com.cataloghub.model.Evento;
import br.com.cataloghub.service.Repositorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Trilha de auditoria de todas as integrações e automações. */
public class LogPanel extends JPanel implements Atualizavel {

    private final Repositorio repo = Contexto.REPO;
    private final DefaultTableModel modelo = Estilo.modelo("Data/hora", "Tipo", "Mensagem");
    private final JTable tabela = Estilo.tabela(modelo);
    private final JComboBox<Object> filtro = new JComboBox<>();

    public LogPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Estilo.FUNDO);
        filtro.addItem("Todos");
        for (Evento.Tipo t : Evento.Tipo.values()) filtro.addItem(t);
        filtro.addActionListener(e -> atualizar());
        add(Estilo.barra(new JLabel("Filtrar por tipo:"), filtro,
                Estilo.botao("Limpar log", () -> { repo.getEventos().clear(); repo.alterado(); })), BorderLayout.NORTH);
        Estilo.larguras(tabela, 150, 100, 900);
        add(Estilo.rolagem(tabela), BorderLayout.CENTER);
        atualizar();
    }

    @Override
    public void atualizar() {
        Object f = filtro.getSelectedItem();
        List<Object[]> dados = new ArrayList<>();
        List<Evento> ev = repo.getEventos();
        for (int i = ev.size() - 1; i >= 0 && dados.size() < 500; i--) {
            Evento e = ev.get(i);
            if (f instanceof Evento.Tipo && e.getTipo() != f) continue;
            dados.add(new Object[]{Estilo.DATA_HORA.format(e.getData()), e.getTipo().name(), e.getMensagem()});
        }
        modelo.setRowCount(0);
        for (Object[] d : dados) modelo.addRow(d);
    }
}
