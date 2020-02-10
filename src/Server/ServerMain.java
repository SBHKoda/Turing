package Server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    //Lista di tutti gli utenti registrati
    private static ConcurrentHashMap<String, Utente> listaUtenti;
    //Lista di tutti i documenti creati
    private static ConcurrentHashMap<String, TDocumento> listaDocumenti;

    private static CopyOnWriteArrayList<InetAddress> multicastAddressList;
    //ServerSocket per iniziare le connessioni con il server
    private static ServerSocket welcomeSocket;
    //Thread-Pool
    private static ExecutorService executorService;

    //--------------------------------------    MAIN    --------------------------------------
    public static void main(String[] args){
        inizializzazioneServer();
        inizializzazioneCicloServer();
    }

    //--------------------------------------    LOGIN   --------------------------------------
    //Metodo per effettuare il login al server, restituisce:
    // 0 In caso di login corretto
    // 1 Utente non registrato
    // 2 In caso l'utente fosse gia loggato
    public static int login(String username, String password){
        //Caso in cui l'utente e` offline e password corretta
        if(!listaUtenti.get(username).checkOnlineStatus() && listaUtenti.get(username).checkPassword(password)){
            listaUtenti.get(username).setOnline();
            return 0;
        }
        //Caso in cui l'utente cerca di accedere con un username non registrato o errato
        if(!listaUtenti.containsKey(username)){
            System.out.println("ERRORE in fase di login, utente non registrato");
            return 1;
        }
        //Caso in cui l'utente cerca di eseguire un doppio login
        if(listaUtenti.get(username).checkOnlineStatus())
            System.out.println("ERRORE in fase di login, utente gia loggato");
        return 2;
    }
    //--------------------------------------    LOGOUT   --------------------------------------
    //Metodo per effettuare il logout dal server, restituisce:
    // true In caso di logout effettuato correttamente
    // false In caso di errore (es. utente non online)
    public static boolean logout(String username){
        //Per effettuare il logout l'utente deve essere online
        if(listaUtenti.get(username).checkOnlineStatus()){
            listaUtenti.get(username).setOffline();
            return true;
        }
        System.out.println("ERRORE in fase di logout, l'utente non era online.");
        return false;
    }
    //--------------------------------------    CREA DOCUMENTO   --------------------------------------
    //Metodo per creare un nuovo documento, restituisce:
    // 0 se il file e` stato correttamente creato
    // 1 se il nome del documento e` gia stato utilizzato
    public static int creaDocumento(String nomeCreatore, String nomeDocumento, int numeroSezioni) throws IOException {
        //Caso in cui esiste gia un documento con questo nome
        if(listaDocumenti.containsKey(nomeDocumento)){
            System.out.println("ERRORE, esiste gia un documento con questo nome, " + nomeDocumento);
            return 1;
        }
        System.out.println("---- Documento " + nomeDocumento + " VALIDO.");

        String tmp= "239." + (int)Math.floor(Math.random()*256) + "." + (int)Math.floor(Math.random()*256) + "." + (int)Math.floor(Math.random()*256);
        InetAddress inetAddress = InetAddress.getByName(tmp);

        while(!inetAddress.isMulticastAddress() && multicastAddressList.contains(inetAddress)){
            inetAddress = InetAddress.getByName(tmp);
            tmp= "239." + (int)Math.floor(Math.random()*256) + "." + (int)Math.floor(Math.random()*256) + "." + (int)Math.floor(Math.random()*256);
        }

        multicastAddressList.add(inetAddress);

        //Aggiorno la lista dei documenti e la lista degli utenti autorizzati alla modifica del documento
        listaDocumenti.put(nomeDocumento, new TDocumento(nomeCreatore, nomeDocumento, numeroSezioni, inetAddress));
        listaUtenti.get(nomeCreatore).addDocumentoAutorizzato(nomeDocumento);

        //Creo una cartella che conterra le varie sezioni che costituiscono il documento
        File directory = new File("TURING_DIRECTORY/" + nomeDocumento);
        directory.mkdir();

        for(int i = 0; i < numeroSezioni; i++){
            File sezione = new File("TURING_DIRECTORY/" + nomeDocumento, nomeDocumento + i +".txt");
            sezione.createNewFile();
        }
        System.out.println("---- Documento : " + nomeDocumento + " CORRETTAMENTE CREATO");
        return 0;
    }

    //------------------------------------------           EDIT-DOC         ------------------------------------------
    //Metodo per editare una sezione di un documento, restituisce:
    // 0 se tutto ok
    // 1 se sezione richiesta gia in modifica
    // 2 se utente non e` ne il creatore del documento ne autorizzato alla modifica
    // 3 se il numero di sezioni inserito non e` valido
    // 4 se non riesco ad acquisire la lock
    public static int editTDoc(String nomeUtente, String nomeDocumento, int numeroSezione, SocketChannel clientSocketChannel){
        TDocumento documento = listaDocumenti.get(nomeDocumento);
        //Numero di sezione non valido
        if(documento.getSections() <= numeroSezione){
            System.out.println("ERRORE, " + numeroSezione + " non e` un numero di sezione valido");
            return 3;
        }
        //Sezione gia in modifica
        if(documento.isLocked(numeroSezione)){
            System.out.println("Impossibile modificare la sezione, sezione gia in modifica");
            return 1;
        }
        //Se non si e` ne creatori ne autorizzati alla modifica
        if(!documento.isCreatore(nomeUtente) && !documento.getListaAutorizzati().contains(nomeUtente)) {
            System.out.println("ERRORE, " + nomeUtente + " non autorizzato alla modifica del documento");
            return 2;
        }
        //Impossibile acquisire la lock sulla sezione
        if(!documento.lockSection(numeroSezione)){
            System.out.println("Impossibile acquisire la lock sulla sezione del documento");
            return 4;
        }
        FileChannel fileChannel;
        try {
            //Apro in lettura il file richiesto
            fileChannel = FileChannel.open(Paths.get("TURING_DIRECTORY/" + nomeDocumento + "/" + nomeDocumento + numeroSezione + ".txt"), StandardOpenOption.READ);
            //Alloco un ByteBuffer per inviare dati al client
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);
            System.out.println("Arrivo qui");
            boolean stop = false;

            // Classica gestione NIO
            while (!stop) {
                int bytesRead = fileChannel.read(buffer);
                if (bytesRead == -1)
                    stop=true;

                buffer.flip();
                while (buffer.hasRemaining())
                    clientSocketChannel.write(buffer);
                buffer.clear();
            }
            //Chiudo i channel ==> trasferimento dati terminato
            clientSocketChannel.close();
            fileChannel.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        System.out.println("--------    "+ nomeUtente + " Modifica Sezione : " + numeroSezione +
                " Documento : " + nomeDocumento + "   --------");
        return 0;
    }
    //------------------------------------------           END-EDIT-DOC         ------------------------------------------
    //Metodo per terminare la modifica di una sezione, aggiorna il file della sezione sia che sia stato modificato o meno
    // usa NIO per ricevere dal client i dati relativi al nuovo file, e in fine sblocca la sezione di file in modo che
    // altri utenti abilitati possano modificare la stessa sezione
    // Restituisce solamente 0
    public static int endEdit(String nomeDocumento, int numeroSezione, SocketChannel socketChannel){
        TDocumento documento = listaDocumenti.get(nomeDocumento);
        File file = new File("TURING_DIRECTORY/" + nomeDocumento, nomeDocumento + numeroSezione + ".txt");
        if(file.exists()) file.delete();
        try {
            file.createNewFile();
            //Apro il file in scrittura e alloco un buffer per ricevere dati dal client con NIO
            FileChannel fileChannel = FileChannel.open(Paths.get("TURING_DIRECTORY/" + nomeDocumento + "/" + nomeDocumento + numeroSezione + ".txt"),	StandardOpenOption.WRITE);
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);

            boolean flag = false;
            // NIO
            while (!flag) {
                int bytesRead = socketChannel.read(buffer);
                if (bytesRead == -1) flag = true;
                buffer.flip();
                while (buffer.hasRemaining())
                    fileChannel.write(buffer);
                buffer.clear();
            }
            //Chiudo i channel ==> trasferimento dati terminato
            socketChannel.close();
            fileChannel.close();

            //Sblocco la sezione di documento in modo che altri possano eventualmente modificarla
            documento.unlockSection(numeroSezione);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return 0;
    }
    //------------------------------------------           DOC-LIST         ------------------------------------------
    //Metodo che restituisce la lista dei documenti che l'utente e` autorizzato a modificare
    public static CopyOnWriteArrayList getDocumentList(String nomeUtente){
        return listaUtenti.get(nomeUtente).getListaDoc();
    }
    //------------------------------------------           SHOW(S, D)         ------------------------------------------
    //Metodo che invia la sezione di file richiesta usando NIO
    // 0 o 1 a seconda che la sezione richiesta sia in modifica o meno da parte di un utente
    // 2 se il documento non esiste
    // 3 se il numero della sezione non ha un valore valido
    // 4 se l'utente non e` autorizzato alla modifica del file

    public static int show(String nomeUtente, String nomeDocumento, int numeroSezione, SocketChannel socketChannel) throws IOException {
        //Documento inesistente
        if(!listaDocumenti.containsKey(nomeDocumento)){
            System.out.println("ERRORE, il documento richiesto non esiste");
            return 2;
        }
        //Numero di sezione non valido
        if(listaDocumenti.get(nomeDocumento).getSections() <= numeroSezione){
            System.out.println("ERRORE, il numero di sezione del documento non e` valido");
            return 3;
        }
        //Utente non autorizzato
        if(!listaUtenti.get(nomeUtente).getListaDoc().contains(nomeDocumento)){
            System.out.println("ERRORE, l'utente non e` autorizzato alla modifica o alla visualizzazione del file");
            return 4;
        }
        int risultato = 0;
        //Controllo se la sezione e` bloccata ==> attualmente in modifica da un utente
        if(listaDocumenti.get(nomeDocumento).isLocked(numeroSezione)){
            risultato = 1;
        }
        //NIO
        String pathFileName = "TURING_DIRECTORY/" + nomeDocumento + "/" + nomeDocumento + numeroSezione + ".txt";
        FileChannel fileChannel = FileChannel.open(Paths.get(pathFileName), StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);

        boolean stop = false;

        // Classica gestione NIO
        while (!stop) {
            int bytesRead = fileChannel.read(buffer);
            if (bytesRead == -1)
                stop = true;

            buffer.flip();
            while (buffer.hasRemaining())
                socketChannel.write(buffer);
            buffer.clear();
        }
        //Chiudo i channel ==> trasferimento dati terminato
        socketChannel.close();
        fileChannel.close();

        return risultato;
    }
    //------------------------------------------           SHOW(D)         ------------------------------------------
    //Metodo che invia l'intero file usando NIO e resetituisce il numero di sezioni attualmente in modifica
    // 0 o n dipende da quante sezioni dell'intero file sono attualmente in modifica
    // 30 se il documento non esiste
    // 31 se l'utente non e` autorizzato alla modifica del file

    public  static int show(String nomeUtente, String nomeDocumento,  SocketChannel socketChannel) throws IOException {
        //Documento inesistente
        if(!listaDocumenti.containsKey(nomeDocumento)){
            System.out.println("ERRORE, il documento richiesto non esiste");
            return 30;
        }
        //Utente non autorizzato
        if(!listaUtenti.get(nomeUtente).getListaDoc().contains(nomeDocumento)){
            System.out.println("ERRORE, l'utente non e` autorizzato alla modifica o alla visualizzazione del file");
            return 31;
        }
        //Conto quante sezioni di questo file sono attualmente in modifica guardando quante sezioni sono attualmente
        // bloccate
        int risultato = 0;
        for(int i = 0; i < listaDocumenti.get(nomeDocumento).getSections(); i++){
            if(listaDocumenti.get(nomeDocumento).isLocked(i)) risultato++;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);
        //Invio tramite NIO tutte le sezioni di file in modo da poter creare un unico file completo
        for(int i = 0; i < listaDocumenti.get(nomeDocumento).getSections(); i++){
            String pathFileName = "TURING_DIRECTORY/" + nomeDocumento + "/" + nomeDocumento + i + ".txt";
            FileChannel fileChannel = FileChannel.open(Paths.get(pathFileName), StandardOpenOption.READ);
            boolean flag = false;

            while (!flag) {
                int bytesRead = fileChannel.read(buffer);
                if (bytesRead == -1) flag = true;
                buffer.flip();
                while (buffer.hasRemaining())
                    socketChannel.write(buffer);
                buffer.clear();
            }
            fileChannel.close();
        }
        socketChannel.close();
        return risultato;
    }

    //------------------------------------------           INVITA         ------------------------------------------
    //Metodo per invitare un utente (autorizzare) alla modifica di un documento
    // 0 se l'invito e` stato correttamente inviato
    // 1 se l'utente che si vuole invitare non e` registrato al servizio
    // 2 se il documento che si vuole autorizzare non esiste
    // 3 se non si e` creatori del documento (solo il creatore puo invitare altri utenti)
    // 4 se l'utente che si vuole invitare e` gia autorizzato alla modifica del documento
    public static int inviteUser(String nomeUtenteInvitato, String nomeUtenteCheInvita, String nomeDocumento) throws RemoteException {
        //Utente non registrato
        if(!listaUtenti.containsKey(nomeUtenteInvitato)){
            System.out.println("ERRORE, l'utente da invitare non e` registrato al servizio");
            return 1;
        }
        //Documento inesistente
        if(!listaDocumenti.containsKey(nomeDocumento)){
            System.out.println("ERRORE, il documento non esiste");
            return 2;
        }
        //Utente non creatore
        if(!listaDocumenti.get(nomeDocumento).isCreatore(nomeUtenteCheInvita)){
            System.out.println("ERRORE, l'utente non e` il creatore del documento");
            return 3;
        }
        //Utente gia` autorizzato
        if(listaUtenti.get(nomeUtenteInvitato).getListaDoc().contains(nomeDocumento)){
            System.out.println("ERRORE, l'utente e` gia autorizzato alla modifica del file");
            return 4;
        }
        //Caso utente OFFLINE
        if(!listaUtenti.get(nomeUtenteInvitato).checkOnlineStatus()){
            System.out.println("INVITO UTENTE OFFLINE");
            listaDocumenti.get(nomeDocumento).addListaAutorizzati(nomeUtenteInvitato);
            listaUtenti.get(nomeUtenteInvitato).addDocumentoAutorizzato(nomeDocumento);
            listaUtenti.get(nomeUtenteInvitato).addInvitoOffline(nomeDocumento);
        }
        //Caso utente ONLINE
        if(listaUtenti.get(nomeUtenteInvitato).checkOnlineStatus()){
            System.out.println("INVITO UTENTE ONLINE");
            listaDocumenti.get(nomeDocumento).addListaAutorizzati(nomeUtenteInvitato);
            listaUtenti.get(nomeUtenteInvitato).addDocumentoAutorizzato(nomeDocumento);
            Utente utente = listaUtenti.get(nomeUtenteInvitato);
            utente.getClientCallback().notifyEvent(nomeDocumento);
        }
        return 0;
    }

    private static void inizializzazioneServer() {
        //Inizializzazione delle strutture dati necessarie per il funzionamento del server
        listaUtenti = new ConcurrentHashMap<>();
        listaDocumenti = new ConcurrentHashMap<>();

        multicastAddressList = new CopyOnWriteArrayList<>();

        //Creo una directory per i documenti nel caso non esistesse
        File directory = new File("TURING_DIRECTORY/");
        if(!directory.exists()) directory.mkdir();

        //Creo la ServerSocket per accettare le connessioni
        try {
            welcomeSocket = new ServerSocket(Configurazione.PORT, 0, InetAddress.getByName(null));
        } catch (IOException e) {
            System.out.println("ERRORE nella creazione della ServerSocket.");
        }

        //Inizializzo il Thread-Pool Executor che gestira le richieste dei client eseguendo task ClientHandler
        executorService = Executors.newCachedThreadPool();

        //Attivcazione del Servizio RMI per la registrazione
        try{
            //creazione istanza oggetto
            ServerImplementationRMI server = new ServerImplementationRMI(listaUtenti);
            //esportazione dell'oggetto
            ServerInterfaceRMI stub = (ServerInterfaceRMI) UnicastRemoteObject.exportObject(server, Configurazione.REG_PORT);
            //creazione di un registry sulla porta in TuringServer.Configurazione
            Registry registry = LocateRegistry.createRegistry(Configurazione.REG_PORT);
            //pubblicazione dello stub nel registry
            registry.bind("Server", stub);

            System.out.println("Server Pronto.");
        } catch (RemoteException | AlreadyBoundException e) {
            e.printStackTrace();
        }
    }

    private static void inizializzazioneCicloServer() {
        //In questo ciclo infinito aspetto i client, quando ne arriva uno creo un socket per il client e creo e avvio un
        //task ClientTask per gestire le sue richieste
        System.out.println("-------------------       Server Pronto       -------------------");
        while (true){
            try {
                Socket clientSocket = welcomeSocket.accept();
                ClientTask clientTask = new ClientTask(clientSocket);
                executorService.execute(clientTask);
                System.out.println("-----                Client Connesso al server              -----");
            } catch (IOException e) {
                System.out.println("-----                Qualcosa e` andato storto              -----");
            }
        }
    }

    public static String getDocAddress(String nomeDocumento){
        String tmp = listaDocumenti.get(nomeDocumento).getMulticastAddress().toString();
        //Tolgo uno "/" all'inizio della stringa
        tmp = (String) tmp.subSequence(1, tmp.length());
        return tmp;
    }
    //Metodo invocato per "unlockare" la sezione del file in caso qualche problema
    public static void unlock(String nomeDocumento, int numSezione) {
        listaDocumenti.get(nomeDocumento).unlockSection(numSezione);
    }
    // Metodi per la restituire e resettare la lista degli inviti offline
    public static CopyOnWriteArrayList<String> getListaInvitiOffline(String username) {
        return listaUtenti.get(username).getInvitiOffline();
    }
    public static void resetInvitiOffLine(String username){
        listaUtenti.get(username).resetInvitiOffline();
    }

    //Ritorna true --> se l'utente e` gia` registrato al servizio callback per le notifiche
    public static boolean getRegisterForCallbackStatus(String username){
        return listaUtenti.get(username).getRegisteredForCallback();
    }

    // Metodo che viene invocato quando l'utente chiude la GUI con il tasto apposito
    public static void chiusuraForzata(String username) {
        if(username != null){
            if(listaUtenti.get(username).checkOnlineStatus()){
                listaUtenti.get(username).setOffline();
            }
        }
    }
}
