/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

*****************************************************************/

import java.io.*;
import java.util.*;
import java.text.*;

public class Resource {

	public String name;
	public int plTime;
	public int quantity;
	public int cost;

	public Resource(String n, int pl, int q, int c) {
		name = n;
		plTime = pl;
		quantity = q;
		cost = c;
	}

	public Resource() {
		name = "";
		plTime = 0;
		quantity = 0;
		cost = 0;
	}
}
