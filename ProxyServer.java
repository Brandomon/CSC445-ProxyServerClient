import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ProxyServer {
    public static void main(String[] args) {
        // Create a cache with a maximum size of 50 items
        Cache cache = new Cache(50);
        
        // Set an initial timeout of 20 seconds for each client request
        final double initialTimeoutInSeconds = 20.0;
        
        // Set the size of the sliding window for congestion control to 5 packets
        final int WINDOW_SIZE = 5;
        
        // Set the port number that the server will listen on for incoming client requests
        int PORT = 10720;
        
        // Initialize threadID to keep track of the ID number for each thread
        int threadID = 0;
        
        // Declare a Thread object to be used for each thread
        Thread thread = null;

        try {
            for (;;) {
                // Create an infinite loop to continuously handle incoming client requests
                thread = new ProxyServerThread(WINDOW_SIZE, PORT, threadID, initialTimeoutInSeconds, cache);
                
                // Print a message indicating that the thread is starting
                System.out.println("Starting thread:\t" + thread.getName());
                
                // Start the thread
                thread.start();
                
                // Wait for the thread to finish executing before continuing
                thread.join();
                
                // Print a message indicating that the thread has completed
                System.out.println("Completed thread:\t" + thread.getName());
                
                // Increment the thread ID number
                threadID++;
                
                // Wait for 1 second before starting the next thread
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            // If an InterruptedException occurs, print the stack trace
            e.printStackTrace();
        }
    }
    public static class Cache {
        private final ReentrantReadWriteLock reentrantReadWriteLock;
        private final Lock writeLock;
        private final Lock readLock;
        private URL[] urls;
        private HashMap<Integer, byte[]>[] data;

        public Cache(int cacheSize) {
            // Ensure that the 'data' array is never initialized with a value that will cause an error
            if (cacheSize < 1) {
                cacheSize = 16;
            }

            // Ensure that the size of the 'data' array is always a power of 2 to make searching and hashing faster
            int rightShifts = 0;
            while (cacheSize > 1) {
                cacheSize = cacheSize >>> 1;
                rightShifts++;
            }

            int initialSize = cacheSize << (rightShifts + 1);

            // Initialize the read-write lock and the locks for reading and writing
            this.reentrantReadWriteLock = new ReentrantReadWriteLock(true);
            this.writeLock = reentrantReadWriteLock.writeLock();
            this.readLock = reentrantReadWriteLock.readLock();
            
            // Initialize the 'urls' and 'data' arrays with the calculated initial size
            this.urls = new URL[initialSize];
            this.data = new HashMap[initialSize];
        }
        public HashMap<Integer, byte[]> computeIfPresent(URL url) {
            // Calculate the index for the given URL
            int h = url.hashCode();
            int i = h & (data.length - 1);

            // Acquire the read lock and check if the URL is present in the cache
            readLock.lock();
            try {
                if (urls[i] != null && urls[i].toString().equals(url.toString())) {
                    return  data[i];
                }else {
                    return null;
                }
            } finally {
                // Release the read lock
                readLock.unlock();
            }
        }
        public void put(URL url, HashMap<Integer, byte[]> urlData) {
            // Calculate the index for the given URL
            int h = url.hashCode();
            int i = h & (data.length - 1);

            // Acquire the write lock and insert the URL and its data into the cache
            writeLock.lock();
            try {
                urls[i] = url;
                data[i] = urlData;
            } finally {
                // Release the write lock
                writeLock.unlock();
            }
        }
    }
    
    public static class ProxyServerThread extends Thread {
        private final double INIT_TIMEOUT_SECONDS = 20.0;
        private final int MAXIMUM_NUMBER_OF_SEND_ATTEMPTS = 500;
        private final int DATA_BUFFER_SIZE = 512;
        private final int ACK_BUFFER_SIZE = 4;
        private final int INITIAL_BLOCK_NUMBER = 1;
        private final int port;
        private final int id;
        private HashMap<Integer, byte[]> urlData;
        private DatagramSocket datagramSocket = null;
        private InetAddress inetAddress;
        private Protocol protocol;
        private Double avgLatency;
        private Double stdDevLatency;
        private int WINDOW_SIZE;
        private int clientPort;
        private int messageNumber;
        private boolean endOfStream;
        private boolean errorInTransmission;
        Cache cache;

        public ProxyServerThread(int windowSize, int portNumber, int ID, double initialTimeoutInSeconds, Cache serverCache) {
            super("ProxyServerThread_" + ID);
            this.WINDOW_SIZE = windowSize;
            this.port = portNumber;
            this.id = ID;
            this.urlData = new HashMap<>(8);
            this.avgLatency = initialTimeoutInSeconds;
            this.stdDevLatency = Math.sqrt(avgLatency);
            this.messageNumber = INITIAL_BLOCK_NUMBER;
            this.endOfStream = false;
            this.errorInTransmission = false;
            this.cache = serverCache;

        }
        @Override
        public void run() {
            byte[] buf = new byte[ACK_BUFFER_SIZE + DATA_BUFFER_SIZE];

            try {
                datagramSocket = new DatagramSocket(port);

                // Receive request
                DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
                datagramSocket.receive(datagramPacket);

                // Format request and determine client information
                Packet packet = new Packet(buf, datagramPacket.getLength());
                int operation = packet.getOperation();
                String filename = packet.getFilename();
                String mode = packet.getMode();
                protocol = packet.getProtocol();

                inetAddress = datagramPacket.getAddress();
                clientPort = datagramPacket.getPort();

                if ((operation == Packet.RRQ) && mode.equalsIgnoreCase(Packet.octetMode)) {
                    switch (protocol) {
                        case TFTP:
                            WINDOW_SIZE = 1;
                            transferFileViaTFTPUsingSlidingWindows(filename);
                            //transferFileViaTFTP(filename);
                            break;

                        case SLIDING_WINDOW:
                            transferFileViaTFTPUsingSlidingWindows(filename);
                            break;

                        default:
                            errorInTransmission = true;
                            String errorMessage = "ERROR: Unrecognized Protocol used in header formation.\n\n" +
                                    		      "Protocol used:\t" + protocol.toString() + "\n\n" +
                                    		      "Protocols currently implemented:\n";

                            for (Protocol implementedProtocol : Protocol.values()) {
                                errorMessage += implementedProtocol.toString() + "\n";
                            }
                            sendAndWaitForACK(new Packet(protocol, Packet.ERROR__NOT_DEFINED, errorMessage).getFormattedData());
                            break;
                    }
                } else {
                    sendAndWaitForACK(new Packet(protocol, operation, filename, mode).getFormattedData());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                datagramSocket.close();
            }
        }
        private void transferFileViaTFTP(String filename) throws IOException {
            URL url = new URL(filename);
            System.out.println(url.getProtocol());
            urlData = cache.computeIfPresent(url);

            if (urlData == null) {
                /*
                * Obtain data via HTTP
                * */
                urlData = new HashMap<>(8);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = httpURLConnection.getInputStream();
                byte[] bytes = new byte[DATA_BUFFER_SIZE];
                int byteCount = 0;
                int byteRead;

                while ((byteRead = inputStream.read()) != -1) {
                    if (byteCount == (DATA_BUFFER_SIZE - 1)) {
                        bytes[byteCount] = (byte) byteRead;
                        byteCount = 0;
                        Double latency = sendAndWaitForACK(new Packet(protocol, messageNumber, bytes).getFormattedData());
                        if (latency != null) {
                            avgLatency = computeNewAverage(avgLatency, latency, messageNumber);
                            stdDevLatency = computeNewAverage(stdDevLatency, Math.abs(latency - avgLatency), (messageNumber - 1));
                            urlData.put(messageNumber, bytes);
                            messageNumber++;
                            bytes = new byte[DATA_BUFFER_SIZE];
                        }else {
                            inputStream.close();
                            httpURLConnection.disconnect();
                            return;
                        }
                    }else {
                        bytes[byteCount] = (byte) byteRead;
                        byteCount++;
                    }
                }

                if (byteCount != 0) {
                    byte[] buf = bytes;
                    bytes = new byte[byteCount];
                    for (int i = 0; i < byteCount; i++) {
                        bytes[i] = buf[i];
                    }
                }else {
                    bytes = new byte[1];
                    bytes[0] = 0b00000000;
                }
                urlData.put(messageNumber, bytes);

                Double latency = sendAndWaitForACK(new Packet(protocol, messageNumber, bytes).getFormattedData());
                if (latency != null) {
                    avgLatency = computeNewAverage(avgLatency, latency, messageNumber);
                    stdDevLatency = computeNewAverage(stdDevLatency, Math.abs(latency - avgLatency), (messageNumber - 1));
                }
                inputStream.close();
                httpURLConnection.disconnect();
                cache.put(url, urlData);

            }else {

                for (messageNumber = INITIAL_BLOCK_NUMBER; messageNumber < urlData.size() + INITIAL_BLOCK_NUMBER; messageNumber++) {
                    Double latency = sendAndWaitForACK(new Packet(protocol, messageNumber, urlData.get(messageNumber)).getFormattedData());
                    if (latency != null) {
                        avgLatency = computeNewAverage(avgLatency, latency, messageNumber);
                        stdDevLatency = computeNewAverage(stdDevLatency, Math.abs(latency - avgLatency), (messageNumber - 1));
                    } else if (errorInTransmission) {
                        break;
                    }
                }

            }
        }
        private void transferFileViaTFTPUsingSlidingWindows(String filename) throws IOException {
            WindowManager windowManager =
                    new WindowManager(this, datagramSocket, WINDOW_SIZE, filename, avgLatency, MAXIMUM_NUMBER_OF_SEND_ATTEMPTS);
            windowManager.transferFile();
        }
        private Double sendAndWaitForACK(byte[] formattedData) throws IOException {
            byte[] acknowledgement = new byte[formattedData.length];
            double secondsPerNanosecond = 1.0e-9;
            double millisecondsPerSecond = 1.0e3;
            double latency = 0.0;
            int timeout = Math.max(1, Math.toIntExact(Math.round((avgLatency + 2.0*stdDevLatency) * millisecondsPerSecond)));
            int sendAttempts = 0;
            boolean ackReceived = false;

            while (!ackReceived && (sendAttempts < MAXIMUM_NUMBER_OF_SEND_ATTEMPTS)) {
                DatagramPacket datagramPacket = new DatagramPacket(formattedData, formattedData.length, inetAddress, clientPort);
                latency = System.nanoTime();
                datagramSocket.send(datagramPacket);

                if (!errorInTransmission) {
                    datagramPacket = new DatagramPacket(acknowledgement, acknowledgement.length);
                    datagramSocket.setSoTimeout(timeout);

                    try {
                        datagramSocket.receive(datagramPacket);
                        latency = (System.nanoTime() - latency) * secondsPerNanosecond;
                        Packet receivedPacket = new Packet(datagramPacket.getData(), datagramPacket.getLength());
                        int receivedOperation = receivedPacket.getOperation();

                        if (receivedOperation == Packet.ACK) {
                            int receivedBlockNumber = receivedPacket.getBlockNumber();
                            if (receivedBlockNumber == messageNumber) {
                                ackReceived = true;
                            }
                        }else if (receivedOperation == Packet.ERROR) {
                            int errorCode = receivedPacket.getErrorCode();
                            String errorMessage = receivedPacket.getErrMsg();
                            System.out.println("ERROR IN TRANSMISSION:\n" +
                                    		   "Error Code:\t" + errorCode + "\n" +
                                    		   "Error Message:\t" + errorMessage + "\n" +
                                    		   "Discontinuing transmission...");
                            errorInTransmission = true;
                            break;
                        }

                    }catch (SocketTimeoutException socketTimeoutException) {
                        socketTimeoutException.printStackTrace();
                    }
                    sendAttempts++;
                }else {
                    break;
                }
            }
            if (ackReceived) {
                return latency;
            }else {
                if (!errorInTransmission) {
                    /*
                    * Error -- MAXIMUM_NUMBER_OF_SEND_ATTEMPTS were utilized in retrieving the data.
                    * */
                    errorInTransmission = true;
                    String errorMessage = "ERROR: MAXIMUM_NUMBER_OF_SEND_ATTEMPTS were utilized in retrieving the data.";
                    sendAndWaitForACK(new Packet(protocol, Packet.ERROR__NOT_DEFINED, errorMessage).getFormattedData());
                }
                return null;
            }
        }
        private double computeNewAverage(double previousAverage, double newValue, int nTotal) {
            if (nTotal == 0) {
                return 0.0;
            }else if (nTotal == 1) {
                return newValue;
            }else {
                return previousAverage + ((newValue - previousAverage) / (1.0*nTotal));
            }
        }
        protected Protocol getProtocol() {
            return protocol;
        }
        protected InetAddress getInetAddress() {
            return inetAddress;
        }
        protected int getClientPort() {
            return clientPort;
        }
        protected void cacheData(URL url, HashMap<Integer, byte[]> data) {
            cache.put(url, data);
        }
        protected HashMap<Integer, byte[]> checkCache(URL url) {
            return cache.computeIfPresent(url);
        }
    }
    
    public static class WindowManager {
        private final int WINDOW_SIZE;
        private final int DATA_BUFFER_SIZE = 512;
        private final int ACK_BUFFER_SIZE = 4;
        private final int INITIAL_BLOCK_NUMBER = 1;
        private final ProxyServerThread proxyServerThread;
        private final DatagramSocket datagramSocket;
        private final String filename;
        private final String name;
        private final int maxTimeouts;
        private HashMap<Integer, byte[]> urlData;
        private Double avgLatency;
        private Double stdDevLatency;
        private String errorMessage;
        private int errorCode;
        private volatile boolean finalPacketTransmitted;
        private volatile boolean errorInTransmission;
        private volatile boolean transmissionComplete;

        public WindowManager(ProxyServerThread serverThread, DatagramSocket socket, int windowSize, String url,
                             Double applicationTimeoutInSeconds, int maximumNumberOfAllowableTimeouts) {
            this.name = "WindowManager_Size=" + windowSize;
            this.WINDOW_SIZE = windowSize;
            this.proxyServerThread = serverThread;
            this.datagramSocket = socket;
            this.filename = url;
            this.maxTimeouts = maximumNumberOfAllowableTimeouts;
            this.urlData = new HashMap<>(8);
            this.avgLatency = applicationTimeoutInSeconds;
            this.stdDevLatency = Math.sqrt(avgLatency);
            this.errorMessage = "";
            this.errorCode = 0;
            this.finalPacketTransmitted = false;
            this.errorInTransmission = false;
            this.transmissionComplete = false;

        }

        public void transferFile() throws IOException {
            try {
                URL url = new URL(filename);
                urlData = proxyServerThread.checkCache(url);
                if (urlData == null) {
                    /*
                    * Obtain data via HTTP
                    * */
                    urlData = new HashMap<>(8);
                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    InputStream inputStream = httpURLConnection.getInputStream();
                    byte[] bytes = new byte[DATA_BUFFER_SIZE];
                    int messageNumber = INITIAL_BLOCK_NUMBER;
                    int byteCount = 0;
                    int byteRead;

                    while ((byteRead = inputStream.read()) != -1) {
                        if (byteCount == (DATA_BUFFER_SIZE - 1)) {
                            bytes[byteCount] = (byte) byteRead;
                            byteCount = 0;
                            urlData.put(messageNumber, bytes);
                            messageNumber++;
                            bytes = new byte[DATA_BUFFER_SIZE];
                        }else {
                            bytes[byteCount] = (byte) byteRead;
                            byteCount++;
                        }
                    }

                    if (byteCount != 0) {
                        byte[] buf = bytes;
                        bytes = new byte[byteCount];
                        for (int i = 0; i < byteCount; i++) {
                            bytes[i] = buf[i];
                        }
                    }else {
                        bytes = new byte[1];
                        bytes[0] = 0b00000000;
                    }
                    urlData.put(messageNumber, bytes);
                    proxyServerThread.cacheData(url, urlData);
                }

                long previousTime;
                int lastPacketACKd = 0;
                int timeoutCount = 0;
                int nTotal = 0;

                while (!(transmissionComplete || errorInTransmission)) {
                    previousTime = System.nanoTime();

                    for (int i = 1; i <= WINDOW_SIZE; i++) {
                        int blockNumber = lastPacketACKd + i;
                        byte[] data = urlData.get(blockNumber);
                        if (data == null) {
                            break;
                        } else if (data.length < (ACK_BUFFER_SIZE + DATA_BUFFER_SIZE)) {
                            finalPacketTransmitted = true;
                        }

                        byte[] buf = new Packet(proxyServerThread.getProtocol(), blockNumber, data).getFormattedData();
                        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, proxyServerThread.getInetAddress(), proxyServerThread.getClientPort());
                        datagramSocket.send(datagramPacket);
                    }

                    /*
                    * Just receive ACKs and update the lastPacketACKd, and ensure no errors occurred
                    * */
                    byte[] data = new byte[ACK_BUFFER_SIZE + DATA_BUFFER_SIZE];
                    DatagramPacket datagramPacket = new DatagramPacket(data, data.length);

                    try {
                        double millisecondsPerSecond = 1.0e3;
                        int timeout = Math.max(1, Math.toIntExact(Math.round((avgLatency + 2.0*stdDevLatency) * millisecondsPerSecond)));
                        System.out.println(timeout);
                        datagramSocket.setSoTimeout(timeout);
                        //datagramSocket.setSoTimeout(INITIAL_TIMEOUT_MILLIS);
                        datagramSocket.receive(datagramPacket);

                        Packet receivedPacket = new Packet(datagramPacket.getData(), datagramPacket.getLength());
                        int receivedOperation = receivedPacket.getOperation();

                        if (receivedOperation == Packet.ACK) {
                            int receivedBlockNumber = receivedPacket.getBlockNumber();
                            //timeoutCount = 0;

                            if (lastPacketACKd < receivedBlockNumber) {
                                double secondsPerNanosecond = 1.0e-9;
                                double latency = (System.nanoTime() - previousTime) * secondsPerNanosecond;
                                nTotal++;
                                avgLatency = computeNewAverage(avgLatency, latency, nTotal);
                                stdDevLatency = computeNewAverage(stdDevLatency, Math.abs(latency - avgLatency), (nTotal - 1));
                                lastPacketACKd = receivedBlockNumber;
                            }

                            if (receivedBlockNumber == urlData.size() && finalPacketTransmitted) {
                                transmissionComplete = true;
                            }

                        }else if (receivedOperation == Packet.ERROR) {
                            errorCode = receivedPacket.getErrorCode();
                            errorMessage = receivedPacket.getErrMsg();
                            errorInTransmission = true;
                            System.out.println("ERROR IN TRANSMISSION:\n" +
                                    "Error Code:\t" + errorCode + "\n" +
                                    "Error Message:\t" + errorMessage + "\n" +
                                    "Discontinuing transmission...");
                        }

                    } catch (SocketException e) {
                        errorCode = Packet.ERROR__NOT_DEFINED;
                        errorInTransmission = true;
                        System.out.println("ERROR IN TRANSMISSION:\n" +
                                "Location:\tWindowManager\n" +
                                "Error:\tProtocol Error. Potentially UDP error.");
                        e.printStackTrace();
                    } catch (SocketTimeoutException e) {
                        if (finalPacketTransmitted) {
                            timeoutCount++;
                        }
                        if (timeoutCount >= maxTimeouts) {
                            if (!finalPacketTransmitted) {
                                errorCode = Packet.ERROR__NOT_DEFINED;
                                errorMessage = "ERROR: MAXIMUM_NUMBER_OF_ALLOWABLE_TIMEOUTS were utilized in acknowledging the data.";
                                errorInTransmission = true;
                                System.out.println(errorMessage);
                            }else {
                                transmissionComplete = true;
                            }
                        }
                        //e.printStackTrace();
                    }
                }

                if (transmissionComplete) {
                    System.out.println("SUCCESS: Transmission successfully completed. File sent to client...");
                }else if (errorInTransmission) {
                    System.out.println("Error in transmission...");
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        private double computeNewAverage(double previousAverage, double newValue, int nTotal) {
            if (nTotal == 0) {
                return 0.0;
            }else if (nTotal == 1) {
                return newValue;
            }else {
                return previousAverage + ((newValue - previousAverage) / (1.0*nTotal));
            }
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
}