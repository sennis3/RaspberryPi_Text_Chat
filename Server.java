


import java.io.*;
import java.util.*;



import java.net.*;

public class Server {

    int port;
    int count = 1;
    private HashMap<Integer, ClientThread> clientMap = new HashMap<Integer, ClientThread>();
    private ArrayList<Integer> connectedClientIDs = new ArrayList<Integer>();
    ServerThread server;

    Server(int port) {
        this.port = port;
        server = new ServerThread();
        server.start();
    }

    Server() {
        this.port = 5555;
    }
    
    

    // Sends a game info object through the client's output stream
    public void sendClientMessage(int ID, Message msg) {
        try {
            clientMap.get(ID).sendMessage(msg);
        } catch (NullPointerException e) {
        }
    }
    
    public void sendAll(Message msg) {
    	synchronized(connectedClientIDs) {
    	for (int currID : connectedClientIDs) {
    		sendClientMessage(currID, msg);
    	}
    	}
    	/*for (Map.Entry<Integer, ClientThread> mapClient : clientMap.entrySet()) {
    		
    		try {
                ((ClientThread) mapClient).sendMessage(msg);  //TODO Might be problem here
            } catch (NullPointerException e) {
            }
    	}*/
    }

    /*
     * The point of the ServerThread class is to open connections with clients and
     * make corresponding Client Threads
     */
    public class ServerThread extends Thread {
        ServerSocket serverSocket;

        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                while (true) {

                    // Create a new client connection
                    ClientThread c = new ClientThread(serverSocket.accept(), count);
                    System.out.println("New client connected");

                    clientMap.put(count, c);
                    connectedClientIDs.add(count);
                    
                    System.out.println("Before Start");
                    c.start();
                    System.out.println("After Start");
                    
                    count++;
                }
            } catch (Exception e) {
            }
        }
    }// End of server thread

    /*
     * The main point of the ClientThread is to send and receive information to/from
     * the client.
     */
    public class ClientThread extends Thread {
        Socket connection;
        int ID;
        ObjectInputStream in;
        ObjectOutputStream out;

        ClientThread(Socket s, int count) {
            this.connection = s;
            this.ID = count;
        }

        public ClientThread(int ID) {
            this.ID = ID;
        }

        public void run() {
            // Connect the stream
            try {
                in = new ObjectInputStream(connection.getInputStream());
                out = new ObjectOutputStream(connection.getOutputStream());
                connection.setTcpNoDelay(true);
            } catch (Exception e) {

                return;
            }
            
            // Send the client an initial message
            Message msg = new Message();
            msg.messageType = "INIT";
            msg.receiverID = this.ID; // send the client their ID no.
            sendMessage(msg);
            
            System.out.println("After new message");
            
            /*
            synchronized (connectedClientIDs) {
	            Message msgClientList = new Message();
	            msgClientList.messageType = "CLIENT_LIST";
	            msgClientList.clientList = connectedClientIDs;
	            //sendMessage(msgClientList);
	            //sendAll(msgClientList);
	            for (int currID : connectedClientIDs) {
	        		sendClientMessage(currID, msgClientList);
	        	}
            } */
            sendClientList();
            
            System.out.println("After Client List update");

            System.out.println(count);
            System.out.println(connectedClientIDs);
            System.out.println(clientMap);
            
            while (true) {
                try {
                    // Wait for the client to send us data
                    Message data = (Message) in.readObject();
                    parseMessage(data);
                } 
                catch (Exception e) {
                	//e.printStackTrace();
                    clientMap.remove(ID);
                    connectedClientIDs.remove(Integer.valueOf(this.ID));
                    System.out.println("Client" + ID + "Disconnected");
                    
                    /*
                    Message rmsgClientList = new Message();
                    rmsgClientList.messageType = "CLIENT_LIST";
                    rmsgClientList.clientList = connectedClientIDs;
                    sendAll(rmsgClientList);
                    */
                    sendClientList();
                    
                    System.out.println(connectedClientIDs);
                    
                    break;
                }
            }
        } // End of run

        // sends an InfoPass object to a client
        
        public void sendMessage(Message msg) {
            try {
                out.writeObject(msg);
            } catch (IOException e) {
            }
        }
        
        public void sendClientList() {
        	synchronized (connectedClientIDs) {
        		Message msgClientList = new Message();
	            msgClientList.messageType = "CLIENT_LIST";
	            
	            
	            ArrayList<Integer> clients = new ArrayList<Integer>();
	            for(int i = 0; i < connectedClientIDs.size(); i++) {
	            	clients.add(connectedClientIDs.get(i));
	            }
	            msgClientList.clientList = clients;
	            
	            //sendMessage(msgClientList);
	            //sendAll(msgClientList);
	            for (int currID : connectedClientIDs) {
	        		clientMap.get(currID).sendMessage(msgClientList);
	        	}
        	}
        }
        
        boolean clientExists(int num) {
        	for (int clientNum : connectedClientIDs) {
        		if (num == clientNum)
        			return true;
        	}
        	return false;
        }
		
        public void parseMessage(Message msg) {
        	String msgType = msg.messageType;

            // Switch statement to check message type
            switch (msgType) {
            case "INIT":
            	System.out.println("Shouldn't be receiving this tag");
              break;
            case "MESSAGE":
            	if (clientExists(msg.receiverID))
            		sendClientMessage(msg.receiverID, msg);
              break;
            case "CLIENT_LIST":
            	System.out.println("Shouldn't be receiving this tag");
              break;
            default:
              // Do nothing in the default
            	System.out.println("Not a valid tag");
            }
        }

    } // End of ClientThread
    
    public static void main(final String args[]) throws InterruptedException {
    	System.out.println("Server Running");
    	Server theServer = new Server(5555);
    }

} // End of Server class

class Message implements Serializable {
	  int senderID, receiverID;
	  protected static final long serialVersionUID = 1112122200L;
	  public String message;
	  public String messageType;
	  public ArrayList<Integer> clientList;
}
