import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Random;
//***************************************************************************************************
//					TO CHANGE SETTINGS:
//	Line 29 : Adjust output file name according to server used / window size / simulated drops
//	Line 30 : Adjust simulated drops to true or false
//	ProxyServer: Line 24: Adjust window size
//***************************************************************************************************
public class ProxyClient {
    public static void main(String[] args) throws IOException, InterruptedException {
    	// Check if arguments were passed to the program
        if (args.length > 0) {
            // If arguments were passed, use them to get files
            getFilesUsingParams(args);
        // Else if no arguments were passed, use default values to get files
        } else {        	
            // Initialize instance variables to default values
            final String[] filenames = {"https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/330px-Cat_November_2010-1a.jpg", "https://assets2.cbsnewsstatic.com/hub/i/r/2012/10/23/135c744d-a645-11e2-a3f0-029118418759/thumbnail/620x465/d72e9eb43850c45843f36e138bc033f5/ALEX_LabradorRetriever_7years2.jpg"};
            final int operation = Packet.RRQ;
            final String mode = Packet.octetMode;
            final String clientHostName = "Home";
            final String serverHostName = "localHost";
            //final String serverHostName = "moxie.cs.oswego.edu";
            final String outputDirectory = "Output";
            final String testResultDirectory = "Output\\TestResults";
            final String testResultFile = "Throughput Results between " + clientHostName + " and LOCALHOST (WindowSize = 100 - No Simulated Drops)";			//CHANGE ACCORDING TO SERVERS / WINDOW SIZE / SIMULATED DROPS
            final boolean simulateDrops = false;																									//CHANGE TO TRUE OR FALSE FOR PACKET DROPPING

            // Create an array to store test results for each file
            TestResult[] testResults = new TestResult[filenames.length * 4];
            
            // Set the protocol to use and other variables for the first set of files
            Protocol protocol = Protocol.SLIDING_WINDOW;
            int port = 10720;
            int count = 0;
            int ID = 1;
            
            // Set the Internet protocol version to 4 for the first set of files
            int internetProtocolVersion = 4;

            // Loop through each file and get it using a FileClient object
            for (int i = 0; i < filenames.length; i++) {
                FileClient fileClient = new FileClient(protocol, ID, internetProtocolVersion, simulateDrops, serverHostName, port, filenames[i], mode, operation);
                fileClient.getAndWriteFile(outputDirectory);
                
                // Store the test results for the file
                testResults[count] = fileClient.getTestResult();
                
                // Print the test results for the file
                System.out.println(testResults[count].printFileTransferResult());
                
                // If the transmission finished, increment the count and ID
                if (fileClient.finishedTransmission()) {
                    count++;
                    ID++;
                // Else if the transmission didn't finish, retry the same file
                } else {
                    i--;
                }
                // Wait for 10 seconds before getting the next file
                Thread.sleep(10000);
            }
            // Set the Internet protocol version to 6 for the second set of files
            internetProtocolVersion = 6;

            // Loop through each file again and get it using a FileClient object
            for (int i = 0; i < filenames.length; i++) {
                // Create a FileClient object and get the file
                FileClient fileClient = new FileClient(protocol, ID, internetProtocolVersion, simulateDrops, serverHostName, port, filenames[i], mode, operation);
                fileClient.getAndWriteFile(outputDirectory);
                
                // Store the test results for the file
                testResults[count] = fileClient.getTestResult();
                
                // Print the test results for the file
                System.out.println(testResults[count].printFileTransferResult());
                
                // If the transmission finished, increment the count and ID
                if (fileClient.finishedTransmission()) {
                    count++;
                    ID++;
                } else {
                    i--;
                }
                Thread.sleep(10000);
            }
            // Write test results to output file
            writeTestResults(testResultDirectory, testResultFile, filenames.length, testResults);
        }
    }
    private static void getFilesUsingParams(String[] args) throws InterruptedException {
    	// Define an array of filenames to retrieve
        final String[] filenames = {"https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/330px-Cat_November_2010-1a.jpg", "https://assets2.cbsnewsstatic.com/hub/i/r/2012/10/23/135c744d-a645-11e2-a3f0-029118418759/thumbnail/620x465/d72e9eb43850c45843f36e138bc033f5/ALEX_LabradorRetriever_7years2.jpg"};
        
        // Initialize instance variables
        final int operation = Packet.RRQ;
        final String mode = Packet.octetMode;
        final String clientHostName = "Home";
        final String serverHostName = "localHost";
        final String outputDirectory = "Output";
        final String testResultDirectory = "Output\\TestResults";
        final String testResultFile = "Throughput Results of " + clientHostName + " and Pi (WindowSize = 5 - With Simulated Drops)";
        
        // Create an array of TestResult objects to store transfer statistics for each file
        TestResult[] testResults = new TestResult[filenames.length * 4];
        
        // Set port, count, and ID variables for use in loop
        int port = 2690;
        int count = 0;
        int ID = 1;

        // Define protocol, IP version, and simulate drops variables and read them from command line arguments, if present
        Protocol protocol = Protocol.SLIDING_WINDOW;
        int internetProtocolVersion = 4;
        boolean simulateDrops = false;

        for (String arg : args) {
            if (arg.contains("protocol")) {
                String value = arg.substring(arg.indexOf("=") + 1);
                if (value.equalsIgnoreCase("TFTP")) {
                    protocol = Protocol.TFTP;
                }
            } else if (arg.contains("ipv")) {
                String value = arg.substring(arg.indexOf("=") + 1);
                if (value.equalsIgnoreCase("4") || value.equalsIgnoreCase("6")) {
                    internetProtocolVersion = Integer.parseInt(value);
                }
            } else if (arg.contains("simulateDrops")) {
                String value = arg.substring(arg.indexOf("=") + 1);
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    simulateDrops = Boolean.parseBoolean(value);
                }
            }
        }

        // Loop through each filename and retrieve it using a FileClient object
        for (int i = 0; i < filenames.length; i++) {
            FileClient fileClient = new FileClient(protocol, ID, internetProtocolVersion, simulateDrops, serverHostName, port, filenames[i], mode, operation);
            fileClient.getAndWriteFile(outputDirectory);
            
            // Store transfer statistics in array and print them
            testResults[count] = fileClient.getTestResult();
            System.out.println(testResults[count].printFileTransferResult());
            
            // Increment count and ID if file transfer finished successfully, otherwise repeat current iteration
            if (fileClient.finishedTransmission()) {
                count++;
                ID++;
            } else {
                i--;
            }
            // Wait 10 seconds before moving on to next file
            Thread.sleep(10000);
        }
        // Write test results to output file
        writeTestResults(testResultDirectory, testResultFile, filenames.length, testResults);
    }

    private static void writeTestResults(String directory, String filename, int numberOfFilesTested, TestResult[] testResults) {
    	// Create a File object for the specified directory.
        File dir = new File(directory);
        
        // Check if the directory exists, if not create it.
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Create a File object for the specified filename in the specified directory.
        File file = new File(dir + File.separator + filename.replaceAll(" ", "_") + ".dat");

        // Try writing the number of files tested to the file.
        try (FileWriter fileWriter = new FileWriter(file)) {
            // Write the number of files tested to the file.
            fileWriter.write(numberOfFilesTested + "\n");
            // Loop through the array of test results and write each non-null result to the file.
            for (TestResult testResult : testResults) {
                if (testResult != null) {
                    fileWriter.write(testResult.printFileTransferResult());
                }
            }
        } catch (IOException e) {
            System.out.println("Unable to write test results for test: " + filename + ".");
            e.printStackTrace();
        }
    }
    public static class FileClient {
    	// Class Constants & Variables
        private final double INIT_TIMEOUT_SECONDS = 2.0;
        private final int SEND_ATTEMPT_MAX_NUM = 100;
        private final int DATA_BUFFER_SIZE = 512;
        private final int ACK_BUFFER_SIZE = 4;
        private final int INITIAL_BLOCK_NUMBER = 1;
        private final int RANDOM_DROP_NUM = 77;
        private final int FOUR = 4;
        private final int SIX = 6;
        private final Protocol protocol;
        private final String fileClientName;
        private final String hostname;
        private final String filename;
        private final String mode;
        private final int operation;
        private final int hostPort;
        private final int ipv;
        private final boolean simulatingDrops;
        private HashMap<Integer, byte[]> fileData;
        private HashMap<Integer, Long> latencyData;
        private DatagramSocket datagramSocket = null;
        private InetAddress inetAddress;
        private Double avgLatency;
        private Double stdDevLatency;
        private boolean lastPacketReceived;
        private boolean endOfStream;
        private boolean transmissionError;

        // FileClient Class Constructor
        public FileClient(Protocol desiredTransferProtocol, int ID, int internetProtocolVersion, boolean simulateDrops,
                          String host, int portNumber, String file, String transferMode, int desiredOperation) {
            // Initialize instance variables
            this.protocol = desiredTransferProtocol;
            this.fileClientName = "FileClient_" + ID;
            this.ipv = internetProtocolVersion;
            this.simulatingDrops = simulateDrops;
            this.hostname = host;
            this.filename = file;
            this.mode = transferMode;
            this.operation = desiredOperation;
            this.hostPort = portNumber;
            this.fileData = new HashMap<>(8);
            this.latencyData = new HashMap<>(8);
            this.avgLatency = INIT_TIMEOUT_SECONDS;
            this.stdDevLatency = Math.sqrt(avgLatency);
            this.lastPacketReceived = false;
            this.endOfStream = false;
            this.transmissionError = false;
        }
        public boolean getAndWriteFile(String directory) throws InterruptedException {
            try {
            	// Initialize the DatagramSocket
                datagramSocket = new DatagramSocket();
                // Set the destination IP address based on the Internet protocol version
                switch (ipv) {
                    case FOUR:
                        inetAddress = Inet4Address.getByName(hostname);
                        break;
                    case SIX:
                        inetAddress = Inet6Address.getByName(hostname);
                        break;
                    default:
                        inetAddress = InetAddress.getByName(hostname);
                        break;
                }
                // Retrieve the file using the specified protocol
                switch (protocol) {
                    case TFTP:
                        retrieveFileViaTFTP();
                        break;

                    case SLIDING_WINDOW:
                        retrieveFileViaTFTPUsingSlidingWindows();
                        break;
                        
                    // If an unrecognized protocol is used, display an error message and return false
                    default:
                        String errorMessage = "Unrecognized Protocol used in header formation...\n\n" +
                                "Protocol:\t" + protocol.toString() + "\n\n" +
                                "Protocols currently implemented:\n";
                        for (Protocol implementedProtocol : Protocol.values()) {
                            errorMessage += implementedProtocol.toString() + "\n";
                        }
                        errorMessage += "Returning to main application...";
                        System.out.println(errorMessage);
                        return false;

                }
                // If the end of the stream is reached, write the received file to disk and return true
                if (endOfStream) {
                    System.out.println("Transmission successfully completed. Writing file to disk...");
                    writeReceivedFile(directory);
                    return true;
                } else {
                    // If an error occurs during transmission, display an error message and return false
                    System.out.println("Error in transmission. Transmission incapable of being recovered...");
                    return false;
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            	// Close the DatagramSocket
                datagramSocket.close();
            }
            // If an error occurs, return false
            return false;
        }
        private void retrieveFileViaTFTP() throws IOException, InterruptedException {
        	// Form a request packet and send it to the server
            byte[] buf = new Packet(protocol, operation, filename, mode).getFormattedData();
            // Initialize variables
            Double latency;
            int expectedBlockNumber = INITIAL_BLOCK_NUMBER;
            
            // Loop until end of stream or error in transmission
            while (!(endOfStream || transmissionError)) {
            	 // Send the request packet and wait for a data packet
                latency = sendAndWaitForData(buf, expectedBlockNumber);

                // If data packet is received, form an acknowledgement packet and send it back to the server
                if (latency != null) {
                    buf = new Packet(protocol, expectedBlockNumber).getFormattedData();
                    avgLatency = computeNewAverage(avgLatency, latency, expectedBlockNumber);
                    stdDevLatency = computeNewAverage(stdDevLatency, Math.abs(latency - avgLatency), (expectedBlockNumber - 1));
                    expectedBlockNumber++;

                // Else if end of stream is reached, send one final acknowledgement packet but do not wait for response
                }else if (endOfStream) {                
                    buf = new Packet(protocol, expectedBlockNumber).getFormattedData();
                    expectedBlockNumber++;
                    sendAndWaitForData(buf, expectedBlockNumber);

                // Else if maximum number of send attempts is reached without success, signal error in transmission and send an error packet
                }else if (!transmissionError) {
                    transmissionError = true;
                    String errorMessage = "ERROR: MAXIMUM_NUMBER_OF_SEND_ATTEMPTS were utilized in retrieving the data.";
                    buf = new Packet(protocol, Packet.ERROR__NOT_DEFINED, errorMessage).getFormattedData();
                    sendAndWaitForData(buf, expectedBlockNumber);
                }
            }
        }
        private Double sendAndWaitForData(byte[] buf, int expectedBlockNumber) throws IOException, InterruptedException {
        	// Initialize variables
            Random random = new Random();
            byte[] data = new byte[ACK_BUFFER_SIZE + DATA_BUFFER_SIZE];
            double secondsPerNanosecond = 1.0e-9;
            double millisecondsPerSecond = 1.0e3;
            double latency = 0.0;
            int timeout = Math.max(1, Math.toIntExact(Math.round((avgLatency + 2.0*stdDevLatency) * millisecondsPerSecond)));
            int randomNumber = RANDOM_DROP_NUM + 1;
            int attemptNumber = 0;
            boolean dataReceived = false;

            // Attempt to send and receive packets until data is received or maximum number of attempts is reached
            while (!dataReceived && (attemptNumber < SEND_ATTEMPT_MAX_NUM)) {
                DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, inetAddress, hostPort);
                latency = System.nanoTime();
                datagramSocket.send(datagramPacket);
                
                // Check for simulation of dropped packets
                if (!endOfStream && !transmissionError) {
                    if (simulatingDrops) {
                        randomNumber = random.nextInt(100);
                        System.out.println("Random Number:\t" + randomNumber);
                    }
                    
                    // Delay for exponential backoff with randomized wait time
                    if (randomNumber == RANDOM_DROP_NUM) {
                        System.out.println("Unlucky:\n" +
                                "ExB Number:\t" + expectedBlockNumber + "\n" +
                                "Avg Latency:\t" + avgLatency + "\n" +
                                "Std Dev (s):\t" + stdDevLatency);
                        Thread.sleep(timeout);
                    } else {
                        try {
                            // Receive the response packet and calculate the latency
                            datagramPacket = new DatagramPacket(data, data.length);
                            datagramSocket.setSoTimeout(timeout);
                            datagramSocket.receive(datagramPacket);
                            latency = (System.nanoTime() - latency) * secondsPerNanosecond;
                            Packet receivedPacket = new Packet(datagramPacket.getData(), datagramPacket.getLength());
                            int receivedOperation = receivedPacket.getOperation();

                            // Process the received packet based on its operation
                            if (receivedOperation == Packet.DATA) {
                                int receivedBlockNumber = receivedPacket.getBlockNumber();
                                byte[] receivedData = receivedPacket.getData();
                                if (receivedBlockNumber == expectedBlockNumber) {
                                    int packetLength = receivedData.length;
                                    if (packetLength == DATA_BUFFER_SIZE) {
                                        fileData.putIfAbsent(expectedBlockNumber, receivedData);
                                    } else if (packetLength < DATA_BUFFER_SIZE) {
                                        fileData.putIfAbsent(expectedBlockNumber, receivedData);
                                        lastPacketReceived = true;
                                        endOfStream = true;
                                    }
                                    dataReceived = true;
                                }
                                
                            // If an error packet is received, print the error code and message and set errorInTransmission to true
                            } else if (receivedOperation == Packet.ERROR) {
                                int errorCode = receivedPacket.getErrorCode();
                                String errorMessage = receivedPacket.getErrMsg();
                                System.out.println("ERROR IN TRANSMISSION:\n" +
                                        "Error Code:\t" + errorCode + "\n" +
                                        "Error Message:\t" + errorMessage + "\n" +
                                        "Discontinuing transmission...");
                                transmissionError = true;
                                break;
                            }
                        } catch (SocketTimeoutException socketTimeoutException) {
                            socketTimeoutException.printStackTrace();
                        }
                    }
                    
                    // Increment attemptNumber
                    attemptNumber++;
                    
                // Else break from loop
                } else {
                    break;
                }
            }

            // Check if data was received and return latency
            if (dataReceived) {
                return latency;
            } else {
                return null;
            }
        }
        private void retrieveFileViaTFTPUsingSlidingWindows() throws IOException, InterruptedException {
        	// Initialize variables
            Random random = new Random();
            int lowestContinuouslyTransmittedBlock;
            int nTotal = 0;
            int timeoutCount = 0;
            
            // Keep looping until there is an error in transmission or the end of stream is reached
            while (!(transmissionError || endOfStream)) {
                // Calculate timeout value based on average latency and standard deviation
                double millisecondsPerSecond = 1.0e3;
                long previousTime;
                int timeout = Math.max(1, Math.toIntExact(Math.round((avgLatency + 2.0*stdDevLatency) * millisecondsPerSecond)));
                
                // Generate a random number for simulating packet drops if enabled
                int randomNumber = RANDOM_DROP_NUM + 1;
                boolean packetReceived = false;

                // Print timeout and random number if simulating packet drops
                //System.out.println("Timeout:\t" + timeout);
                if (simulatingDrops) {
                    randomNumber = random.nextInt(100);
                    System.out.println("Random Number:\t" + randomNumber);
                }
                
                // Send the lowest continuously received ACK and set endOfStream if last packet is received
                previousTime = System.nanoTime();
                lowestContinuouslyTransmittedBlock = sendLowestContinuouslyReceivedACK();

                if (lastPacketReceived && (lowestContinuouslyTransmittedBlock == fileData.size())) {
                    endOfStream = true;
                } else {
                    // If the random number generated is the random drop number, sleep for the timeout duration
                    if (randomNumber == RANDOM_DROP_NUM) {
                        System.out.println("Unlucky:\n" +
                                "LCTB Number:\t" + lowestContinuouslyTransmittedBlock + "\n" +
                                "Avg Latency:\t" + avgLatency + "\n" +
                                "Std Dev (s):\t" + stdDevLatency);
                        Thread.sleep(timeout);
                        packetReceived = false;
                        
                    // Otherwise, try to receive a packet within the timeout duration
                    } else {
                        packetReceived = receivePacket(previousTime, timeout, timeoutCount, lowestContinuouslyTransmittedBlock, nTotal);
                    }
                }
                // Increment nTotal if packet was received
                if (packetReceived) {
                    timeoutCount = 0;
                    nTotal++;
                    
                // Otherwise increment timeout count
                } else {
                    timeoutCount++;
                }
            }
        }


        private int sendLowestContinuouslyReceivedACK() throws IOException {
            // Get the lowest block number that has been transmitted continuously by the client
            int lowestContinuouslyTransmittedBlock = getLowestContinuouslyTransmittedBlock();
            
            // If client has received packets
            if (lowestContinuouslyTransmittedBlock != 0) {
                // Create an ACK packet with the lowest block number
                byte[] buf = new Packet(protocol, lowestContinuouslyTransmittedBlock).getFormattedData();
                DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, inetAddress, hostPort);
                
                // Record the time the ACK was sent for calculating latency
                latencyData.put(lowestContinuouslyTransmittedBlock, System.nanoTime());
                
                // Send the ACK packet
                datagramSocket.send(sendPacket);
                
            // Else if the client hasn't received any packets yet, send a request packet for the file
            } else {
                byte[] buf = new Packet(protocol, operation, filename, mode).getFormattedData();
                DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, inetAddress, hostPort);
                latencyData.put(lowestContinuouslyTransmittedBlock, System.nanoTime());
                datagramSocket.send(sendPacket);
            }
            return lowestContinuouslyTransmittedBlock;
        }


        private boolean receivePacket(long previousTimeNanoseconds, int timeout, int nTimeouts, int lctb, int nTotal) {
        	// Create a byte array for storing the received data
            byte[] data = new byte[ACK_BUFFER_SIZE + DATA_BUFFER_SIZE];
            
            // Create a datagram packet for receiving the data
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);
            
            try {
                // Set the socket timeout duration
                datagramSocket.setSoTimeout(timeout);
                
                // Receive the packet from the server
                datagramSocket.receive(receivePacket);

                // Convert the received data to a Packet object and process it
                Packet receivedPacket = new Packet(receivePacket.getData(), receivePacket.getLength());
                return processPacket(receivedPacket, lctb, nTotal, previousTimeNanoseconds);

            } catch (SocketException e) {
                transmissionError = true;
                
                // If there is a SocketException, it means there is a protocol error or a UDP error
                System.out.println("ERROR IN TRANSMISSION:\n" +
                        "Error:\t" + "Protocol Error. Potentially UDP error.");
                e.printStackTrace();
                return false;
            } catch (SocketTimeoutException e) {
                // If there is a SocketTimeoutException, it means a timeout occurred while waiting for a response
                nTimeouts++;
                if (nTimeouts >= SEND_ATTEMPT_MAX_NUM) {
                    transmissionError = true;
                    System.out.println("ERROR: MAXIMUM_NUMBER_OF_SEND_ATTEMPTS were utilized in retrieving the data.");
                }
                e.printStackTrace();
                return false;
            } catch (IOException e) {
            	// If there is an IOException that is not related to a timeout, there is an error in transmission
                transmissionError = true;
                System.out.println("ERROR IN TRANSMISSION:\n" +
                        "Error:\t" + "Non-Timeout Related IO Error.");
                e.printStackTrace();
                return false;
            }
        }


        private boolean processPacket(Packet packet, int lctb, int nTotal, long previousTimeNanoseconds) {
        	// Get the operation code from the Packet object
            int receivedOperation = packet.getOperation();

            // If the operation code is for data transmission
            if (receivedOperation == Packet.DATA) {
                // Get the block number and data from the Packet object
                int receivedBlockNumber = packet.getBlockNumber();
                byte[] receivedData = packet.getData();
                int packetLength = receivedData.length;

                // If the current block number is greater than the last successfully transmitted block number
                if (lctb < receivedBlockNumber) {
                    // Calculate the latency of this transmission
                    double secondsPerNanosecond = 1.0e-9;
                    double latency = (System.nanoTime() - previousTimeNanoseconds) * secondsPerNanosecond;
                    
                    // Increment the total number of received blocks
                    nTotal++;
                    
                    // Update the average latency and standard deviation of latency based on the new transmission
                    avgLatency = computeNewAverage(avgLatency, latency, nTotal);
                    stdDevLatency = computeNewAverage(stdDevLatency, Math.abs(latency - avgLatency), (nTotal - 1));
                    
                    // Add the received data to the file data map.
                    fileData.putIfAbsent(receivedBlockNumber, receivedData);
                }
                // If the length of the received packet is less than the maximum data buffer size
                if (packetLength < DATA_BUFFER_SIZE) {
                    // Set a flag indicating that the last packet has been received
                    lastPacketReceived = true;
                }
                // Return true to indicate successful packet processing
                return true;

            // Else if the operation code indicates an error in transmission
            } else if (receivedOperation == Packet.ERROR) {
                // Set a flag indicating that there has been a transmission error
                transmissionError = true;
                
                // Print an error message to the console
                System.out.println("ERROR IN TRANSMISSION:\n" +
                        "Error Code:\t" + packet.getErrorCode() + "\n" +
                        "Error Message:\t" + packet.getErrMsg() + "\n" +
                        "Ending transmission...");
                
                // Return false to indicate failed packet processing
                return false;
                
            // Else if the operation code is not recognized
            } else {
                // Set a flag indicating that there has been a transmission error
                transmissionError = true;
                
                // Print an error message to the console
                System.out.println("ERROR IN TRANSMISSION:\n" +
                        "Unexpected Operation:\t" + receivedOperation + "\n" +
                        "Ending transmission...");
                
                // Return false to indicate failed packet processing
                return false;
            }

        }
        
    	// Computes a new average based on the previous average, new value, and total number of values
        private double computeNewAverage(double previousAverage, double newValue, int nTotal) {
            if (nTotal == 0) {
                return 0.0;
            }else if (nTotal == 1) {
                return newValue;
            }else {
                return previousAverage + ((newValue - previousAverage) / (1.0*nTotal));
            }
        }

        private void writeReceivedFile(String directory) throws IOException {
            // Create the directory if it doesn't exist
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // Determine the filename and create a new file object
            String name = filename.contains("/") ? filename.substring(filename.lastIndexOf("/") + 1) : filename;
            name = directory + File.separator + name;
            File file = new File(name);
            
            // Create a new file output stream and compute file information
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            int numberOfDataPackets = fileData.size();
            int lastDataPacketSize = fileData.get(fileData.size()).length;
            int fileSizeInBytes = (numberOfDataPackets - 1)* DATA_BUFFER_SIZE + lastDataPacketSize;
            int index = 0;
            byte[] bytes = new byte[fileSizeInBytes];

            // Iterate over all the packets and copy their data into a byte array
            for (int i = INITIAL_BLOCK_NUMBER; i < fileData.size() + INITIAL_BLOCK_NUMBER; i++) {
                byte[] packet = fileData.get(i);
                for (int j = 0; j < packet.length; j++) {
                    bytes[index] = packet[j];
                    index++;
                }
            }
            
            // Write the byte array to the output stream
            fileOutputStream.write(bytes);
        }
        
    	// Gets the lowest continuously transmitted block number
        private int getLowestContinuouslyTransmittedBlock() {
            int blockNumber;
            for (blockNumber = INITIAL_BLOCK_NUMBER; blockNumber <= fileData.size(); blockNumber++) {
                if (!fileData.containsKey(blockNumber)) {
                    return blockNumber - 1;
                }
            }
            return blockNumber - 1;
        }
        
    	// Returns true if the transmission has finished
        public boolean finishedTransmission() {
            return lastPacketReceived && endOfStream;
        }
        public TestResult getTestResult() {
            // Compute various statistics about the transmission test
            int fileSizeInBytes = ((ACK_BUFFER_SIZE + DATA_BUFFER_SIZE)*(fileData.size()-1) + fileData.get(fileData.size()).length);
            double bytesToMegabytes = 1.0e-6;
            double approxThroughput = (8.0 * fileSizeInBytes * bytesToMegabytes) / (fileData.size() * avgLatency);
            double dT_dL = -approxThroughput/avgLatency;
            double approxThroughputSD = Math.sqrt((dT_dL*dT_dL) * (stdDevLatency*stdDevLatency));

            // Return a new TestResult object with the computed statistics
            return new TestResult(fileClientName, avgLatency, stdDevLatency,
                    approxThroughput, approxThroughputSD, fileSizeInBytes, fileData.size());
        }
    }
    
    public static class Packet {
    	// Class Constants
        public static final int ERROR__NOT_DEFINED = 1;
        public static final int ERROR__FILE_NOT_FOUND = 2;
        public static final int ERROR__ACCESS_VIOLATION = 4;
        public static final int ERROR__DSK_FULL_OR_ALLOC_EXCD = 8;
        public static final int ERROR__ILLEGAL_TFTP_OPERATION = 16;
        public static final int ERROR__UNKNOWN_TRANSFER_ID = 32;
        public static final int ERROR__FILE_ALREADY_EXISTS = 64;
        public static final int ERROR__NO_SUCH_USER = 128;
        public static final int RRQ = 1;
        public static final int WRQ = 2;
        public static final int DATA = 3;
        public static final int ACK = 4;
        public static final int ERROR = 5;
        public static final String netascii = "netascii";
        public static final String octetMode = "octet";
        public static final String mail = "mail";
        private final byte[] formattedData;

        public Packet(Protocol protocol, int operation, String filename, String mode) {
            if (operation != RRQ) {
                String errorMessage = getOperationErrorMessage(operation);
                formattedData = new Packet(protocol, ERROR__ILLEGAL_TFTP_OPERATION, errorMessage).getFormattedData();
            }else if(!mode.equalsIgnoreCase(octetMode)) {
                String errorMessage = getModeErrorMessage(mode);
                formattedData = new Packet(protocol, ERROR__ILLEGAL_TFTP_OPERATION, errorMessage).getFormattedData();
            }else {
                byte[] filenameBytes = filename.getBytes();
                byte[] modeBytes = mode.getBytes();
                byte[] protocolBytes = protocol.toString().getBytes();
                int formattedDataLength;
                int counter = 0;
                switch (protocol) {
                    case TFTP:
                        formattedDataLength = filenameBytes.length + modeBytes.length + protocolBytes.length + 5;
                        formattedData = new byte[formattedDataLength];

                        formattedData[0] = 0b00000000;
                        formattedData[1] = 0b00000001;
                        counter = 2;

                        for (int i = 0; i < filenameBytes.length; i++, counter++) {
                            formattedData[counter] = filenameBytes[i];
                        }

                        formattedData[counter] = 0b00000000;
                        counter++;

                        for (int i = 0; i < modeBytes.length; i++, counter++) {
                            formattedData[counter] = modeBytes[i];
                        }

                        formattedData[counter] = 0b00000000;
                        counter++;

                        for (int i = 0; i < protocolBytes.length; i++, counter++) {
                            formattedData[counter] = protocolBytes[i];
                        }

                        formattedData[counter] = 0b00000000;

                        break;

                    case SLIDING_WINDOW:
                        formattedDataLength = filenameBytes.length + modeBytes.length + protocolBytes.length + 5;
                        formattedData = new byte[formattedDataLength];

                        formattedData[0] = 0b00000000;
                        formattedData[1] = 0b00000001;
                        counter = 2;

                        for (int i = 0; i < filenameBytes.length; i++, counter++) {
                            formattedData[counter] = filenameBytes[i];
                        }

                        formattedData[counter] = 0b00000000;
                        counter++;

                        for (int i = 0; i < modeBytes.length; i++, counter++) {
                            formattedData[counter] = modeBytes[i];
                        }

                        formattedData[counter] = 0b00000000;
                        counter++;

                        for (int i = 0; i < protocolBytes.length; i++, counter++) {
                            formattedData[counter] = protocolBytes[i];
                        }

                        formattedData[counter] = 0b00000000;

                        break;

                    default:
                        String errorMessage = "";
                        formattedData = new Packet(protocol, ERROR__NOT_DEFINED, errorMessage).getFormattedData();
                        break;
                }
            }
        }

        public Packet(Protocol protocol, int blockNumber, byte[] data) {
            int formattedDataLength;
            int counter = 0;
            switch (protocol) {
                case TFTP:
                    formattedDataLength = 4 + data.length;
                    formattedData = new byte[formattedDataLength];

                    formattedData[0] = 0b00000000;
                    formattedData[1] = 0b00000011;
                    formattedData[2] = (byte) ((blockNumber << 16) >>> 24);
                    formattedData[3] = (byte) ((blockNumber << 24) >>> 24);
                    counter = 4;

                    for (int i = 0; i < data.length; i++, counter++) {
                        formattedData[counter] = data[i];
                    }

                    break;

                case SLIDING_WINDOW:
                    formattedDataLength = 4 + data.length;
                    formattedData = new byte[formattedDataLength];

                    formattedData[0] = 0b00000000;
                    formattedData[1] = 0b00000011;
                    formattedData[2] = (byte) ((blockNumber << 16) >>> 24);
                    formattedData[3] = (byte) ((blockNumber << 24) >>> 24);
                    counter = 4;

                    for (int i = 0; i < data.length; i++, counter++) {
                        formattedData[counter] = data[i];
                    }

                    break;

                default:
                    String errorMessage = "";
                    formattedData = new Packet(protocol, ERROR__NOT_DEFINED, errorMessage).getFormattedData();
                    break;

            }
        }

        public Packet(Protocol protocol, int blockNumber) {
            int formattedDataLength;
            switch (protocol) {
                case TFTP:
                    formattedDataLength = 516;
                    formattedData = new byte[formattedDataLength];

                    formattedData[0] = 0b00000000;
                    formattedData[1] = 0b00000100;
                    formattedData[2] = (byte) ((blockNumber << 16) >>> 24);
                    formattedData[3] = (byte) ((blockNumber << 24) >>> 24);
                    break;

                case SLIDING_WINDOW:
                    formattedDataLength = 516;
                    formattedData = new byte[formattedDataLength];

                    formattedData[0] = 0b00000000;
                    formattedData[1] = 0b00000100;
                    formattedData[2] = (byte) ((blockNumber << 16) >>> 24);
                    formattedData[3] = (byte) ((blockNumber << 24) >>> 24);
                    break;

                default:
                    String errorMessage = "";
                    formattedData = new Packet(protocol, ERROR__NOT_DEFINED, errorMessage).getFormattedData();
                    break;

            }
        }

        public Packet(Protocol protocol, int errorCode, String errorMessage) {
            byte[] message;
            int formattedDataLength;
            switch (protocol) {
                case TFTP:
                    message = errorMessage.getBytes();
                    formattedDataLength = message.length + 5;
                    formattedData = new byte[formattedDataLength];

                    formattedData[0] = 0b00000000;
                    formattedData[1] = 0b00000101;
                    formattedData[2] = (byte) ((errorCode << 16) >>> 24);
                    formattedData[3] = (byte) ((errorCode << 24) >>> 24);

                    for (int i = 0; i < message.length; i++) {
                        formattedData[4+i] = message[i];
                    }

                    formattedData[formattedDataLength - 1] = 0b00000000;

                    break;

                case SLIDING_WINDOW:
                    message = errorMessage.getBytes();
                    formattedDataLength = message.length + 5;
                    formattedData = new byte[formattedDataLength];

                    formattedData[0] = 0b00000000;
                    formattedData[1] = 0b00000101;
                    formattedData[2] = (byte) ((errorCode << 16) >>> 24);
                    formattedData[3] = (byte) ((errorCode << 24) >>> 24);

                    for (int i = 0; i < message.length; i++) {
                        formattedData[4+i] = message[i];
                    }

                    formattedData[formattedDataLength - 1] = 0b00000000;

                    break;

                default:
                    errorMessage += getProtocolErrorMessage(protocol);
                    errorCode |= ERROR__NOT_DEFINED;

                    message = errorMessage.getBytes();
                    formattedDataLength = message.length + 5;
                    formattedData = new byte[formattedDataLength];

                    formattedData[0] = 0b00000000;
                    formattedData[1] = 0b00000101;
                    formattedData[2] = (byte) ((errorCode << 16) >>> 24);
                    formattedData[3] = (byte) ((errorCode << 24) >>> 24);

                    for (int i = 0; i < message.length; i++) {
                        formattedData[4+i] = message[i];
                    }

                    formattedData[formattedDataLength - 1] = 0b00000000;

                    break;

            }
        }

        public Packet(byte[] buffer, int length) {
            byte[] data = new byte[length];
            System.arraycopy(buffer, 0, data, 0, length);
            formattedData = data;
        }

        private String getOperationErrorMessage(int operation) {
            return "ERROR: Unrecognized Operation Code used in header formation.\n\n" +
                    "Operation Code used:\t" + operation + "\n\n" +
                    "Operations Codes available:\n" +
                    "RRQ:\t" + 1 + "\n" +
                    "DATA:\t" + 3 + "\n" +
                    "ACK:\t" + 4 + "\n" +
                    "ERROR:\t" + 5 + "\n";
        }

        private String getModeErrorMessage(String mode) {
            return "ERROR: Unrecognized Mode requested in header formation.\n\n" +
                    "Mode requested:\t" + mode + "\n\n" +
                    "Modes available:\n" +
                    "octet\n";
        }

        private String getProtocolErrorMessage(Protocol protocolUsed) {
            String protocolErrorMessage = "ERROR: Unrecognized Protocol used in header formation.\n\n" +
                    "Protocol used:\t" + protocolUsed.toString() + "\n\n" +
                    "Protocols currently implemented:\n";

            for (Protocol implementedProtocol : Protocol.values()) {
                protocolErrorMessage += implementedProtocol.toString() + "\n";
            }

            return protocolErrorMessage;
        }

        public byte[] getFormattedData() {
            return formattedData;
        }

        public int getOperation() {
            return (formattedData[0] << 8) | (formattedData[1]);
        }

        public String getFilename() {
            byte indicator = 0b00000000;
            int offset = 2;
            int length = 0;

            while (formattedData[offset + length] != indicator) {
                length++;
            }

            //int filenameBytes = formattedData.length - ("octet".getBytes().length + 4);
            return new String(formattedData, offset, length);

        }

        public String getMode() {
            byte indicator = 0b00000000;
            int offset = 2;
            int length = 0;

            while (formattedData[offset++] != indicator) {}

            while (formattedData[offset + length] != indicator) {
                length++;
            }

            //int filenameBytes = formattedData.length - ("octet".getBytes().length + 4);
            return new String(formattedData, offset, length);

        }

        public Protocol getProtocol() {
            byte indicator = 0b00000000;
            int offset = 2;
            int length = 0;

            while (formattedData[offset++] != indicator) {}

            while (formattedData[offset++] != indicator) {}

            while (formattedData[offset + length] != indicator) {
                length++;
            }

            //int filenameBytes = formattedData.length - ("octet".getBytes().length + 4);
            String protocol = new String(formattedData, offset, length);
            if (protocol.equalsIgnoreCase(Protocol.TFTP.toString())) {
                return Protocol.TFTP;
            }else if (protocol.equalsIgnoreCase(Protocol.SLIDING_WINDOW.toString())) {
                return Protocol.SLIDING_WINDOW;
            }else {
                return null;
            }

        }

        public int getBlockNumber() {
            return (formattedData[2] << 8) | (formattedData[3]);
        }

        public byte[] getData() {
            byte[] data = new byte[formattedData.length - 4];
            for (int i = 0; i < data.length; i++) {
                data[i] = formattedData[i+4];
            }

            return data;
        }

        public int getErrorCode() {
            return (formattedData[2] << 8) | (formattedData[3]);
        }

        public String getErrMsg() {
            byte indicator = 0b00000000;
            int offset = 4;
            int length = 0;

            while (formattedData[offset + length] != indicator) {
                length++;
            }
            return new String(formattedData, offset, length);
        }
    }
    
    public static enum Protocol {
        TFTP, SLIDING_WINDOW;
    }
    
    public static class TestResult {
        private final String testName;
        private final long[] rawLatencyData;
        private final double[] rawThroughputData;
        private final int dataSize;
        private final int N;
        private double maxLatency;
        private double minLatency;
        private double meanLatency;
        private double latencySD;
        private double maxThroughput;
        private double minThroughput;
        private double meanThroughput;
        private double throughputSD;

        public TestResult(String testID, long[] latencyTimes, double[] throughputs, int dataSizeInBytes, int n) {
            testName = testID;
            rawLatencyData = latencyTimes;
            rawThroughputData = throughputs;
            dataSize = dataSizeInBytes;
            N = n;
            computeThroughputResults();
            computeLatencyResults();

        }

        public TestResult(String testID, long latencyTime, double throughput, int dataChunkSize, int dataChunks) {
            testName = testID;
            rawLatencyData = new long[]{latencyTime};
            rawThroughputData = new double[]{throughput};
            dataSize = dataChunkSize;
            N = dataChunks;
            meanLatency = latencyTime;
            meanThroughput = throughput;
            latencySD = 0.0;
            throughputSD = 0.0;
        }

        public TestResult(TestResult[] testResults) {
            testName = testResults[0].testName;
            dataSize = testResults[0].dataSize;
            N = testResults[0].N;

            long[] latencyTimes = new long[testResults.length];
            double[] throughputs = new double[testResults.length];

            for (int i = 0; i < testResults.length; i++) {
                latencyTimes[i] = Math.round(testResults[i].meanLatency);
                throughputs[i] = Math.round(testResults[i].meanThroughput);
            }

            rawLatencyData = latencyTimes;
            rawThroughputData = throughputs;
            computeThroughputResults();
            computeLatencyResults();

        }

        public TestResult(String testID, double avgLatency, double stdDevLatency, double approxTrhpt, double stdDevApproxThrpt, int fileSizeInBytes, int packetsSent) {
            testName = testID;
            rawLatencyData = new long[]{Math.round(avgLatency)};
            rawThroughputData = new double[]{approxTrhpt};
            dataSize = fileSizeInBytes;
            N = packetsSent;

            meanLatency = avgLatency;
            latencySD = stdDevLatency;
            meanThroughput = approxTrhpt;
            throughputSD = stdDevApproxThrpt;
        }

        private void computeThroughputResults() {
            maxThroughput = rawThroughputData[0];
            minThroughput = rawThroughputData[0];
            meanThroughput = 0.0;
            throughputSD = 0.0;
            for (int i = 0; i < rawThroughputData.length; i++) {
                if (rawThroughputData[i] > maxThroughput) {
                    maxThroughput = rawThroughputData[i];
                }
                if (rawThroughputData[i] < minThroughput) {
                    minThroughput = rawThroughputData[i];
                }
                meanThroughput += rawThroughputData[i];
            }
            meanThroughput = (meanThroughput/(1.0*rawThroughputData.length));

            for (int i = 0; i < rawThroughputData.length; i++) {
                throughputSD += Math.pow((rawThroughputData[i] - meanThroughput), 2.0);
            }
            throughputSD = Math.sqrt(throughputSD/(1.0*rawThroughputData.length));

        }

        private void computeLatencyResults() {
            maxLatency = rawLatencyData[0];
            minLatency = rawLatencyData[0];
            meanLatency = 0.0;
            latencySD = 0.0;
            for (int i = 0; i < rawLatencyData.length; i++) {
                if (rawLatencyData[i] > maxLatency) {
                    maxLatency = rawLatencyData[i];
                }
                if (rawLatencyData[i] < minLatency) {
                    minLatency = rawLatencyData[i];
                }
                meanLatency += 1.0*rawLatencyData[i];
            }
            meanLatency = (meanLatency/(1.0*rawLatencyData.length));

            for (int i = 0; i < rawLatencyData.length; i++) {
                latencySD += Math.pow((1.0*rawLatencyData[i] - meanLatency), 2.0);
            }
            latencySD = Math.sqrt(latencySD/(1.0*rawLatencyData.length));

        }

        public String printResult() {
            return "Data Size:\t" + dataSize + "\n" +
                    "N:\t" + N + "\n" +
                    "Latency Final Results:\t" + meanLatency + " +/- " + latencySD + "\n" +
                    "Throughput Final Results:\t" + meanThroughput + " +/- " + throughputSD + "\n";
        }

        public String printFileTransferResult() {
            return String.format("%10d %20.15e %20.15e %20.15e %20.15e\n",
                    dataSize, meanThroughput, throughputSD, meanLatency, latencySD);
        }

        public String toString() {
            return String.format("%10d %20.15e %20.15e %20.15e %20.15e %20.15e %20.15e %20.15e %20.15e\n",
                    dataSize, meanThroughput, throughputSD, maxThroughput, minThroughput, meanLatency, latencySD, maxLatency, minLatency);
        }

    }
}