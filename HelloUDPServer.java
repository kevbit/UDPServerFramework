package edu.sjsu.cs158a;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.security.*;


public class HelloUDPServer {
	private static final short HELLO = 1;
	private static final short TRANSFER = 2;
	private static final short CHECKSUM = 3;
	private static final short ERROR = 5;
	static int conversationID = 0;
	static DatagramSocket sock;
	static int port;
	
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException{
    	if(args.length == 0) {
    		System.err.println("Missing required parameter: '<port>'\n"
    				+ "Usage: HelloUDPServer <port>\n"
    				+ "A UDP server that implements the HelloUDP protocol.\n"
    				+ "      <port>   port to listen on.");
    		System.exit(2);
    	}
    	else if(args.length == 1) {
    		var argument = args[0]; 
    		try {
    			port = Integer.parseInt(argument);
    			if(port < 0 || port > 65535) {
    				System.err.println("port must be a number between 0 and 65535\n"
    						+ "Usage: HelloUDPServer <port>\n"
    						+ "A UDP server that implements the HelloUDP protocol.\n"
    						+ "      <port>   port to listen on.");
    				System.exit(2);
    	    	}
    	    	else {
    	    		System.out.println("Listening on port " + port);
    	    		sock = new DatagramSocket(port);
    	        	var receiveBytes = new byte[512];
    	    		var receivePacket = new DatagramPacket(receiveBytes, receiveBytes.length);
    	    		HashMap<Integer, String> convoToClient = new HashMap<>();
    	    		HashMap<String, Integer> clientToConvo = new HashMap<>();
    	    		
    	        	while(true) {
    	        		try {
    	        			sock.receive(receivePacket);
    	        		} 
    	        		catch(SocketException e) { //Error handling
    	        			var otherErrorMessage = "The server ran into an error.".getBytes();
    	        			var errorBytes = new byte[otherErrorMessage.length + 2];
    	        			var errorByteBuffer = ByteBuffer.wrap(errorBytes);
    	        			errorByteBuffer.putShort(ERROR);
    	        			errorByteBuffer.put(otherErrorMessage);
    	        			var errorPacket = new DatagramPacket(errorBytes, errorBytes.length, receivePacket.getAddress(), receivePacket.getPort());
    	        			sock.send(errorPacket);
    	        		}
    	        		
    	        		var bb = ByteBuffer.wrap(receivePacket.getData());
    	        		short type = checkType(bb); 
    	        		//Continuously scans for possible new clients by checking all HELLO messages
    	        		if(type == HELLO) { //Offset 0 since we already incremented it in checkType by 2
    	        			System.out.println("From " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ": " + type + " " + conversationID);	
    	        			var firstMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
    	        			System.out.println(firstMessage);
    	        					var clientName = firstMessage.substring(16);
    	        					convoToClient.put(conversationID, clientName); //Stores conversationIDs with clientNames for future reference
    	        					clientToConvo.put(clientName, conversationID);
    	        					//Send back our response
    	        					var sendFirstMessage = "hello, i am kevin lo".getBytes();
	    	        				var sendBytes = new byte[sendFirstMessage.length + 6];
		    	        			var sendBuffer = ByteBuffer.wrap(sendBytes);
	    	        				sendBuffer.putShort(HELLO).putInt(conversationID);
	    	        				sendBuffer.put(sendFirstMessage);
    	        				
	    	        				var sendPacket = new DatagramPacket(sendBytes, sendBytes.length, receivePacket.getAddress(), receivePacket.getPort());
	    	        				try {
	    	        					sock.send(sendPacket);
	    	            				System.out.println("To " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ": " + type + " " + conversationID);
	    	        				} 
	    	        				catch(SocketException e) {
	    	        					sock.send(sendPacket);
	    	            				System.out.println("To " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ": " + type + " " + conversationID);
	    	        				}
	    	        				conversationID ++;
    	        		}
    	        		else if(type == TRANSFER) {        			
    	        			var localConversationID = bb.getInt();
    	        			System.out.println("From " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ": " + type + " " + localConversationID);
    	        		
    	        			var clientName = convoToClient.get(localConversationID);
    	        			String fileName = clientName + ".txt";
    	        			RandomAccessFile outputFile = new RandomAccessFile(fileName, "rw");
    	        			
    	        			int offset = bb.getInt();
    	        			System.out.println("Offset: " + offset);
    	        			byte[] processedData = Arrays.copyOfRange(receivePacket.getData(), 10, receivePacket.getLength());
    	        			
    	        			outputFile.seek(offset);	
    	        			outputFile.write(processedData, 0, processedData.length);
    	        			outputFile.close();
    	        			
    	        			//Send back acknowledgement
    	        			var ackBytes = new byte[10];
    	        			var ackbb = ByteBuffer.wrap(ackBytes);
    	        			ackbb.putShort(TRANSFER).putInt(localConversationID).putInt(offset);
    	        			var ackPacket = new DatagramPacket(ackBytes, ackBytes.length, receivePacket.getAddress(), receivePacket.getPort());
    	        			try {
    	        				sock.send(ackPacket);
    	        				System.out.println("To " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ": " + type + " " + localConversationID);
    	        			}
    	        			catch (SocketException e) {
    	        				var otherErrorMessage = "The server ran into an error.".getBytes();
    	            			var errorBytes = new byte[otherErrorMessage.length + 2];
    	            			var errorByteBuffer = ByteBuffer.wrap(errorBytes);
    	            			errorByteBuffer.putShort(ERROR);
    	            			errorByteBuffer.put(otherErrorMessage);
    	            			var errorPacket = new DatagramPacket(errorBytes, errorBytes.length, receivePacket.getAddress(), receivePacket.getPort());
    	            			sock.send(errorPacket);
    	        			}
    	        		}
    	        		else if (type == CHECKSUM) {
    	        			int localConversationID = bb.getInt();
    	        			var checkSumBytes = new byte[8];
    	        			bb.get(checkSumBytes);
    	        			var clientName = convoToClient.get(localConversationID);
    	        			
    	        			var fileName = clientName + ".txt";
    	        			MessageDigest outputDigest = MessageDigest.getInstance("SHA-256");
    	        			//Continue to create the buffer for the digest read
    	        			byte[] byteBuffer = new byte[100];
    	        			int bytesProcessed;
    	        			FileInputStream is = new FileInputStream(fileName);
    	        			
    	        			while ((bytesProcessed = is.read(byteBuffer)) != -1) {
    	        			    outputDigest.update(byteBuffer, 0, bytesProcessed);
    	        			}
    	        			is.close();
    	        			byte[] outputDigestBytes = outputDigest.digest();
    	        			
    	        			byte[] localDigest = Arrays.copyOfRange(outputDigestBytes, 0, 8);
    	        			
    	        			
    	        			if(Arrays.equals(localDigest, checkSumBytes)) {
    	        				var checkBytes = new byte[7];	
    	        				var checksumbb = ByteBuffer.wrap(checkBytes);
    	        				checksumbb.putShort(CHECKSUM).putInt(localConversationID).put((byte) 0);
    	        				var checkSumPacket = new DatagramPacket(checkBytes, checkBytes.length, receivePacket.getAddress(), receivePacket.getPort());
    	        				sock.send(checkSumPacket);
    	        				System.out.println("To " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ": " + type + " " + localConversationID);
    	        			}
    	        			else {
    	        				var checkBytes = new byte[7];	
    	        				var checksumbb = ByteBuffer.wrap(checkBytes);
    	        				checksumbb.putShort(CHECKSUM).putInt(localConversationID).put((byte) 1);
    	        				var checkSumPacket = new DatagramPacket(checkBytes, checkBytes.length, receivePacket.getAddress(), receivePacket.getPort());
    	        				sock.send(checkSumPacket);
    	        				System.out.println("To " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ": " + type + " " + localConversationID);
    	        			}
    	   
    	        			
    	        		}
    	        		
    	        	}
    	        	
    	    	}
    	    }
    		catch (NumberFormatException e) {
    			System.err.println("port must be a number between 0 and 65535\n"
    					+ "Usage: HelloUDPServer <port>\n"
    					+ "A UDP server that implements the HelloUDP protocol.\n"
    					+ "      <port>   port to listen on.");
    			System.exit(2);
    		}
    	}
    	else {
    		System.err.println("Unmatched arguments from index 1: 'goes', 'here'\n"
    				+ "Usage: HelloUDPServer <port>\n"
    				+ "A UDP server that implements the HelloUDP protocol.\n"
    				+ "      <port>   port to listen on.");
    		System.exit(2);
    	}
    }
    	
    public static short checkType(ByteBuffer bb) {
    	short returnType = bb.getShort();
    	return returnType;
    
    }
    
    
}