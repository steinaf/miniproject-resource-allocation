/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

*****************************************************************/

import jade.core.AID;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class ProviderGui extends JFrame {	
	private ProviderAgent myAgent;

	private JTextField titleField,quantityField, priceField, demandRatio;

	ProviderGui(ProviderAgent a) {
		super(a.getLocalName());

		myAgent = a;

		JPanel p = new JPanel();
		p.setLayout(new GridLayout(4, 2));

		p.add(new JLabel("Resource name:"));
		titleField = new JTextField(15);
		p.add(titleField);

		p.add(new JLabel("Quantity:"));
		quantityField = new JTextField(15);
		p.add(quantityField);

		p.add(new JLabel("Price:"));
		priceField = new JTextField(15);
		p.add(priceField);

		p.add(new JLabel("Demand Price Ratio:"));
		demandRatio = new JTextField(15);
		p.add(demandRatio);

		getContentPane().add(p, BorderLayout.CENTER);

		JButton addButton = new JButton("Add");

		addButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				try {
					String title = titleField.getText().trim();
					String price = priceField.getText().trim();
					String demand = demandRatio.getText().trim();
					String quantity = quantityField.getText().trim();
					myAgent.addToCatalogue(title, Integer.parseInt(quantity), Integer.parseInt(price), Double.parseDouble(demand));
					titleField.setText("");
					quantityField.setText("");
					priceField.setText("");
					demandRatio.setText("");
				}
				catch (Exception e) {
					JOptionPane.showMessageDialog(ProviderGui.this, "Invalid values. "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
				}
			}
		} );
		p = new JPanel();
		p.add(addButton);
		getContentPane().add(p, BorderLayout.SOUTH);

		// Make the agent terminate when the user closes 
		// the GUI using the button on the upper right corner	
		/*
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				myAgent.doDelete();
			}
		} );
		*/

		setResizable(false);
	}

	public void showGui() {
		pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int centerX = (int)screenSize.getWidth() / 2;
		int centerY = (int)screenSize.getHeight() / 2;
		setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
		super.setVisible(true);
	}	
}
