import java.net.*;
import java.io.*;
import java.util.*;

/**
 * class ConnectionDaemon handles connection between devices
 */
class ConnectionDaemon extends Thread {
	public Hashtable connections;
	public Hashtable mappings;
	public int cCount;

	// ---The followings are required if we want emulate noise in the medium
	public static double DROP_RATE = 0.0;
	public static double ERROR_RATE = 0.0;

	// =======================================================
	public static void main(String args[]) throws Exception {
		int argCount = args.length;
		if (argCount > 0) {
			ConnectionDaemon.DROP_RATE = Double.parseDouble(args[0]);
			System.out.println("Drop rate is: " + DROP_RATE);
		}
		if (argCount > 1) {
			ConnectionDaemon.ERROR_RATE = Double.parseDouble(args[1]);
			System.out.println("Error rate is: " + ERROR_RATE);
		}

		ConnectionDaemon cDaemon = new ConnectionDaemon(DROP_RATE, ERROR_RATE);
		cDaemon.join();
	}

	// =======================================================

	// my cd with param
	public ConnectionDaemon(double dr, double er) {
		this.DROP_RATE = dr;
		this.ERROR_RATE = er;
		connections = new Hashtable();
		mappings = new Hashtable();
		start();
	}

	// =======================================================
	public void run() {
		Res.CD_OUT = "Connection Daemon Started.\n Drop rate: "+DROP_RATE+"\nError rate: "+ERROR_RATE+"\n" ;
		MainPanel.jlConnectionDaemonOutput.setText(Res.CD_OUT);
		try {
			ServerSocket servSoc = new ServerSocket(9009);// server with port
															// number
			loadTopology();
			while (true) {
				Socket sock = servSoc.accept();
				new Connection(sock, this);
				cCount++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ----------Load the topology--------------
	public void loadTopology() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(
				"..//Config//Config.txt"));
		// System.out.println("Loading Topology.");
		String line;
		while ((line = br.readLine()) != null) {
			String[] tokens = line.split(":");
			if (tokens.length > 1 && tokens[1].compareTo("CONNECTSTO") == 0) {
				// System.out.println(line);
				String oneEnd = tokens[0];
				String otherEnd = tokens[2];
				mappings.put(oneEnd, otherEnd); // can be done using single
												// entry
				mappings.put(otherEnd, oneEnd);
			}
		}
		Res.CD_OUT += "Topology loaded\n";
		MainPanel.jlConnectionDaemonOutput.setText(Res.CD_OUT);
	}

	// ------------Register an incoming device-----------
	synchronized public void registerConnection(String deviceId, Connection c) {
		connections.put(deviceId, c);
		System.out.println("Connections Registered For " + deviceId);

		// check if other end is already registered
		String otherEnd = (String) mappings.get(deviceId);
		Connection o = (Connection) connections.get(otherEnd);
		if (o != null) {
			System.out.println(deviceId + " connected to " + otherEnd);
			Res.CD_OUT += "\n" + deviceId + " connected to " + otherEnd;
			MainPanel.jlConnectionDaemonOutput.setText(Res.CD_OUT);
		} else {
			Res.CD_OUT += "\n Something is wrong! ";
			MainPanel.jlConnectionDaemonOutput.setText(Res.CD_OUT);
		}

	}

	// ---------Function used to send data from one end to another--------------
	public void sendData(String deviceId, byte[] term) {
		Connection c = (Connection) connections.get(deviceId);
		String otherEnd = (String) mappings.get(deviceId);
		if (otherEnd != null) {
			Connection o = (Connection) connections.get(otherEnd);
			if (o != null) {
				byte[] temp = addError(term);
				if (temp != null) {
					o.send(term);
					System.out.println("Sent from " + deviceId + " to "
							+ otherEnd);
					Res.CD_OUT += "\nSent from " + deviceId + " to " + otherEnd;
					MainPanel.jlConnectionDaemonOutput.setText(Res.CD_OUT);
				}
			}
		}
	}

	// -------Function that may be used to add errors to frame to emulate noise
	// in the medium
	byte[] addError(byte[] b) {
		// Drop Frame Randomly
		double dpp = Math.random();
		if (dpp < DROP_RATE) {
			System.out.println("Dropping Frame:  " + new Frame(b).getString()
					+ "\n");
			return null;
		}
		// add random error before transmitting
		double fep = Math.random();
		if (fep < ERROR_RATE) {
			// toggle k random bits
			// int k = (int) (Math.random() * (b.length)) + 1; // value of k is
			// // random
			// for (int i = 0; i < k; i++)
			// b[i] = (byte) (~b[(int) (Math.random() * (b.length + 1))]);

			int k = (int) (Math.random() * (b.length)); // value of k is random
			for (int i = 0; i < k; i++)
				b[i] = (byte) (~b[(int) (Math.random() * (b.length - 1))]);
			System.out.println("Frame after error:  "
					+ new Frame(b).getString() + "\n");
			return b;
		}
		return b;
	}
	// =======================================================
}

/**
 * class Connection connects the devices through sockets
 */
class Connection extends Thread {
	String deviceId;
	Socket sock;
	ConnectionDaemon cDaemon;

	DataInputStream br;
	OutputStream bw;

	// =========================================================
	Connection(Socket sc, ConnectionDaemon cd) throws Exception {
		sock = sc;
		cDaemon = cd;
		br = new DataInputStream(sock.getInputStream());
		bw = sock.getOutputStream();

		start();
	}

	public void send(byte[] message) {
		try {
			SimPhy.writeStuffed(bw, message);
		} catch (Exception e) {
		}
	}

	// =========================================================
	public void run() {
		try {
			byte[] b = SimPhy.readDeStuffed(br);
			deviceId = new String(b);
			cDaemon.registerConnection(deviceId, this);

			while (true) {
				b = SimPhy.readDeStuffed(br);
				cDaemon.sendData(deviceId, b);
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}
}
