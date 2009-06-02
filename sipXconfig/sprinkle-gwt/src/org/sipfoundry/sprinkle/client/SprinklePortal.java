/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */

package org.sipfoundry.sprinkle.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DecoratedTabPanel;
import com.google.gwt.user.client.ui.DecoratorPanel;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.StackPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

class VoicemailPanel extends Composite {
    public VoicemailPanel() {
        SuggestBox searchBox = new SuggestBox();
        searchBox.setWidth("100%");

        HorizontalPanel footer = new HorizontalPanel();
        footer.setWidth("100%");
        HTML total = new HTML("357 items");
        footer.add(total);
        footer.setCellHorizontalAlignment(total, HasHorizontalAlignment.ALIGN_LEFT);
        HTML show = new HTML("Show: 50");
        footer.add(show);
        footer.setCellHorizontalAlignment(show, HasHorizontalAlignment.ALIGN_RIGHT);

        DockPanel panel = new DockPanel();
        panel.setWidth("100%");
        panel.setSpacing(5);
        panel.add(footer, DockPanel.SOUTH);
        panel.add(createTree(), DockPanel.WEST);
        panel.add(searchBox, DockPanel.NORTH);
        panel.add(createVoiceMailTable(), DockPanel.CENTER);

        initWidget(panel);
    }

    private Widget createVoiceMailTable() {
        FlexTable voiceMailTable = new FlexTable();
        voiceMailTable.setCellPadding(5);

        String[] headers = {
            "From", "Time", "Length"
        };
        for (int i = 0; i < headers.length; i++) {
            voiceMailTable.setWidget(0, i, new Label(headers[i]));
        }

        DecoratorPanel panel = new DecoratorPanel();
        panel.setWidget(voiceMailTable);
        return panel;
    }

    private Tree createTree() {
        Tree tree = new Tree();
        tree.addItem("All Contacts");
        tree.addItem("Friends");
        TreeItem recent = tree.addItem("Recently Added");
        recent.addItem("This week");
        recent.addItem("Last week");
        recent.addItem("This month");
        tree.addItem("Recent Voicemail");

        return tree;
    }
}

class SettingsPanel extends Composite {
    public SettingsPanel() {
        TabPanel panel = new DecoratedTabPanel();

        panel.add(new Label("Forwarding"), "Forwarding");
        panel.add(new ConfigurationPanel(), "Configuration");
        panel.setSize("100%", "100%");

        initWidget(panel);
    }
}

class ConfigurationPanel extends Composite {
    public ConfigurationPanel() {
        HorizontalPanel buttons = new HorizontalPanel();
        buttons.setSpacing(5);
        buttons.add(new Button("OK"));
        buttons.add(new Button("Cancel"));

        Grid grid = new Grid(2, 2);
        grid.setCellPadding(5);
        grid.setWidget(0, 0, new Label("Pin"));
        grid.setWidget(0, 1, new PasswordTextBox());
        grid.setWidget(1, 0, new Label("E-mail"));
        grid.setWidget(1, 1, new TextBox());

        Grid gridAdvanced = new Grid(1, 2);
        gridAdvanced.setCellPadding(5);
        gridAdvanced.setWidget(0, 0, new Label("Secondary E-mail"));
        gridAdvanced.setWidget(0, 1, new TextBox());
        DisclosurePanel disclosurePanel = new DisclosurePanel("Advanced");
        disclosurePanel.add(gridAdvanced);

        VerticalPanel panel = new VerticalPanel();
        panel.add(grid);
        panel.add(disclosurePanel);
        panel.add(buttons);
        initWidget(panel);
    }
}

public class SprinklePortal implements EntryPoint {

    private static String toSizePx(int size) {
        return String.valueOf(size) + "px";
    }

    public void onModuleLoad() {
        FlowPanel header = new FlowPanel();
        header.add(new Label("User Portal"));

        final StackPanel stack = new StackPanel();
        stack.add(new Label("phonebook goes here"), "My Phonebook");
        stack.add(new VoicemailPanel(), "My Voicemail and Call History");
        stack.add(new Label("conferences go here"), "My Conferences");
        stack.add(new SettingsPanel(), "My Settings");

        final VerticalPanel dock = new VerticalPanel();
        dock.setSpacing(4);
        dock.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

        dock.add(header);
        dock.add(stack);
        // dock.add(new VoicemailPanel(), DockPanel.SOUTH);
        // FIXME: is there a easy way to of seeting this to 100%?
        dock.setHeight(Window.getClientHeight() + "px");
        dock.setWidth(Window.getClientWidth() + "px");
        stack.setWidth(toSizePx(Window.getClientWidth() - 20));
        stack.setHeight(toSizePx(Window.getClientHeight() - 50));
        Window.addResizeHandler(new ResizeHandler() {
            public void onResize(ResizeEvent event) {
                dock.setHeight(toSizePx(event.getHeight()));
                dock.setWidth(toSizePx(event.getWidth()));
                stack.setWidth(toSizePx(event.getWidth() - 20));
            }
        });

        RootPanel rootPanel = RootPanel.get("content");
        rootPanel.add(dock);
    }
}
