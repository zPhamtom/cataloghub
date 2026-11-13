package br.com.cataloghub;

import br.com.cataloghub.service.DadosDemo;
import br.com.cataloghub.ui.Contexto;
import br.com.cataloghub.ui.MainFrame;

import javax.swing.*;

/**
 * CatalogHub - solução acadêmica para a "Dor 2: Gestão de Catálogo e Marketplaces".
 * Integra (de forma simulada) múltiplos canais de venda a partir de um cadastro central.
 */
public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> {
            if (Contexto.REPO.getProdutos().isEmpty()) DadosDemo.popular(Contexto.SYNC, Contexto.PEDIDOS);
            new MainFrame().setVisible(true);
        });
    }
}
