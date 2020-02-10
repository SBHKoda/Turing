package Client;

import Server.Configurazione;
import Server.ServerInterfaceRMI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;

public class ClientUI extends JFrame {

    //Per la comunizazione tra client e server
    private static Socket clientSocket;
    private static BufferedReader ricevoDalServer;
    private static DataOutputStream invioAlServer;
    private static SocketChannel clientSocketChannel = null;

    //Per la chat durante la modifica delle sezioni di file
    private MulticastSocket chatSocket;
    private InetAddress group;
    private Chat chat;
    private Calendar calendar;

    //Per attivare il servizio calback per le notifiche
    private ServerInterfaceRMI stub;
    private ArrayBlockingQueue<String> msgList;
    private NotifyReceiver receiver;

    //Strutture necessarie per la creazione della UI
    private JPasswordField passwordField;
    private JTextField usernameField;
    private JButton loginB;
    private JButton signUpB;
    private JButton logoutB;
    private JButton invitaB;
    private JButton creaTdocB;
    private JButton showSectionB;
    private JButton showDocumentB;
    private JButton editB;
    private JButton listB;
    private JButton endEditB;
    private JLabel statusLabel;

    private boolean onlineStatus = false;

    //Strutture necessarie per la UI modificata per l'edit dei documenti
    private JButton inviaMessaggio;
    private JTextArea chatArea, msgArea, sectionArea;

    private String username;
    private String nomeDocumento;
    private String nomeSezioneModificata;
    private int sezione = -1;
    private String address;


    public ClientUI() throws IOException {
        inizializzazioneClient();
        inizializzazioneUI();
    }

    //--------------------------------------    LOGIN   --------------------------------------
    private void login() {
        try {
            invioAlServer.write(0 );
            username = usernameField.getText();
            invioAlServer.writeBytes(username + '\n');
            invioAlServer.writeBytes(new String(passwordField.getPassword()) + '\n');
            //Ricevo dal Server il risultato
            int risultato = ricevoDalServer.read();
            int callbackAttiva = ricevoDalServer.read();
            System.out.println("----    Risultato ottenuto : " + risultato);
            switch (risultato){
                case 0:     //0 in caso di login corretto
                    onlineStatus = true;
                    statusLabel.setText("OnLine");
                    repaint();

                    //Ricevo dal server gli inviti Offline nel caso ne fossero stati ricevuti
                    StringBuilder tmp = new StringBuilder();
                    int dimensioneLista = ricevoDalServer.read();
                    System.out.println("----    Dimensione lista inviti offline ricevuto : " + dimensioneLista);
                    if(dimensioneLista == 0){
                        tmp = new StringBuilder(ricevoDalServer.readLine());
                        JOptionPane.showMessageDialog(null, tmp.toString());
                    }
                    else{
                        for(int i = 0; i < dimensioneLista; i++)
                            tmp.append(ricevoDalServer.readLine()).append('\n');
                        JOptionPane.showMessageDialog(null, "Inviti ricevuti ai documenti: " + '\n' + tmp, "INVITI RICEVUTI OFFLINE", JOptionPane.INFORMATION_MESSAGE);
                    }

                    if(callbackAttiva == 0){
                        //Registrazione al servizio RMI callback per le notifiche
                        NotifyInterfaceRMI clientCallback = new NotifyImplementationRMI(msgList);
                        int port = generaPorta();
                        UnicastRemoteObject.exportObject(clientCallback, port);
                        stub.registerForCallback(clientCallback, username);
                    }
                    //Avvio il thread per la ricezione delle notifiche
                    receiver = new NotifyReceiver(msgList);
                    receiver.start();

                    if(clientSocketChannel == null)
                        clientSocketChannel = createChannel();
                    break;
                case 1:     //1 in caso di credenziali non corrette
                    JOptionPane.showMessageDialog(null, "Username o Password non validi", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                    break;
                case 2:     //2 in caso l'utente fosse gia loggato
                    JOptionPane.showMessageDialog(null, "Utente già connesso.", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                    break;
                default:
                    JOptionPane.showMessageDialog(null, "Errore imprevisto", "ERRORE", JOptionPane.ERROR_MESSAGE);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //--------------------------------------    SIGN IN   --------------------------------------
    private void signUp() {
        String userN = usernameField.getText();
        String passW = new String(passwordField.getPassword());
        //Controllo che i valori inseriti siano validi prima di inviarli al server per la registrazione
        int risultato = controllaValoriDaRegistrare(userN, passW);
        if(risultato == -1) return;
        //Se arrivo a questo punto i valori inseriti sono validi e posso procedere con la registrazione
        boolean res = false;
        try {
            Registry registry = LocateRegistry.getRegistry(Configurazione.REG_PORT);
            stub = (ServerInterfaceRMI) registry.lookup("Server");
            res = stub.registration(userN, passW);
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
        if(res)
            JOptionPane.showMessageDialog(null, "Utente registrato correttamete", "SUCCESSO", JOptionPane.INFORMATION_MESSAGE);
        else
            JOptionPane.showMessageDialog(null, "Utente gia registrato con questo nome", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
    }
    //--------------------------------------    LOGOUT   --------------------------------------
    private void logout() {
        try {
            if(!onlineStatus){
                JOptionPane.showMessageDialog(null, "Utente gia offline", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
            //Invio il comando al server per eseguire l'operazione di logout
            invioAlServer.write(1);
            //Invio l'username e attendo il risultato dell'op dal server
            invioAlServer.writeBytes(username + '\n');
            int risultato = ricevoDalServer.read();
            if (risultato == 0){
                clientSocket.close();
                clientSocketChannel.close();
                receiver.stopReceiver();
                System.out.println("--------     DISCONNESSO     --------");
                System.exit(1);
            }
            else{
                JOptionPane.showMessageDialog(null, "ERRORE in fase di logout", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //--------------------------------------    CREA DOCUMENTO   --------------------------------------
    private void creaDocumento() {
        String nomeDocumento;
        int numeroSezioni;
        //Creo una finestra con 2 campi di testo per inserire il nome del documento e il numero delle sezioni
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel label = new JPanel(new GridLayout(0, 1, 2, 2));
        label.add(new JLabel("Nome Documento", SwingConstants.RIGHT));
        label.add(new JLabel("Numero Sezioni", SwingConstants.RIGHT));
        panel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField nomeDocumentoField = new JTextField();
        controls.add(nomeDocumentoField);
        JTextField numeroSezioniField = new JTextField();
        controls.add(numeroSezioniField);
        panel.add(controls, BorderLayout.CENTER);
        //Controllo se viene premuto OK o CANCEL
        int input = JOptionPane.showConfirmDialog(null, panel, "CREA DOCUMENTO", JOptionPane.OK_CANCEL_OPTION);
        if(input == 0){//Caso OK
            nomeDocumento = nomeDocumentoField.getText();
            numeroSezioni = Integer.parseInt(numeroSezioniField.getText());
            //Controllo se il numero di sezioni inserito e` valido
            if(numeroSezioni < 2 || numeroSezioni >= Configurazione.MAX_SECTIONS){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, numero di sezioni non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        else return;
        //Invio il comando al server per iniziare la creazione del documento e i valori nomedocumento e numero di sezioni
        try {
            invioAlServer.write(2);     //Comando 2 per la creazione del documento
            System.out.println(username);
            invioAlServer.writeBytes(username + '\n');
            invioAlServer.writeBytes(nomeDocumento + '\n');
            invioAlServer.write(numeroSezioni);
            int risultato = ricevoDalServer.read();
            switch (risultato){
                case 0:     //Caso creazione correttamente riuscita
                    JOptionPane.showMessageDialog(null, "Documento " + nomeDocumento + " creato correttamente");
                    break;
                case 1:     //Caso documento gia presente
                    JOptionPane.showMessageDialog(null, "Documento " + nomeDocumento + " gia presente", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                    break;
                default:
                    JOptionPane.showMessageDialog(null, "ERRORE in fase di creazione documento", "ERRORE", JOptionPane.ERROR_MESSAGE);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //--------------------------------------    EDIT DOCUMENTO   --------------------------------------
    private void edit() throws IOException {
        String nomeDocumento;

        //Creo una finestra con 2 campi di testo per inserire il nome del documento e il numero delle sezioni
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel label = new JPanel(new GridLayout(0, 1, 2, 2));
        label.add(new JLabel("Nome Documento", SwingConstants.RIGHT));
        label.add(new JLabel("Sezione", SwingConstants.RIGHT));
        panel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField nomeDocumentoField = new JTextField();
        controls.add(nomeDocumentoField);
        JTextField numeroSezioniField = new JTextField();
        controls.add(numeroSezioniField);
        panel.add(controls, BorderLayout.CENTER);
        //Controllo se viene premuto OK o CANCEL
        int input = JOptionPane.showConfirmDialog(null, panel, "EDIT DOCUMENT", JOptionPane.OK_CANCEL_OPTION);
        if(input == 0){//Caso OK
            nomeDocumento = nomeDocumentoField.getText();
            this.nomeDocumento = nomeDocumento;
            if (nomeDocumento == null || nomeDocumento.equals("")){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, nome documento inserito non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
            this.sezione = Integer.parseInt(numeroSezioniField.getText());
            //Controllo se il numero di sezione da modificare inserito e` valido
            if(sezione >= Configurazione.MAX_SECTIONS || sezione < 0){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, numero di sezione indicato non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        else return;
        //Invio il comando al server per iniziare la creazione del documento e i valori nomedocumento e numero di sezioni
        invioAlServer.write(3);     //Comando 3 per modifica di una sezione di file
        invioAlServer.writeBytes(username + '\n');
        invioAlServer.writeBytes(nomeDocumento + '\n');
        invioAlServer.write(sezione);

        int risultato = ricevoDalServer.read();
        if (risultato == 0) {
            address = ricevoDalServer.readLine();
            System.out.println(address);
            //Creo una cartella che conterra le varie sezioni che ho chiesto di editare
            File directory = new File("TURING_DIRECTORY_CLIENT/");
            if(!directory.exists()) directory.mkdir();
            File subDirectory = new File("TURING_DIRECTORY_CLIENT/" + nomeDocumento);
            if(!subDirectory.exists())subDirectory.mkdir();
            //Creo un nuovo file e cancello eventualmente quello piu vecchio
            File file = new File("TURING_DIRECTORY_CLIENT/" + nomeDocumento, nomeDocumento + sezione + ".txt");
            if(file.exists()) file.delete();
            file.createNewFile();

            //Caso 0 inizio Download del file
            nomeSezioneModificata = "TURING_DIRECTORY_CLIENT/"+ nomeDocumento + "/" + nomeDocumento + sezione + ".txt";
            FileChannel outChannel = FileChannel.open(Paths.get(nomeSezioneModificata), StandardOpenOption.WRITE);
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
            boolean stop = false;
            // NIO
            while (!stop) {
                int bytesRead = clientSocketChannel.read(buffer);
                if (bytesRead == -1)
                    stop = true;
                buffer.flip();
                while (buffer.hasRemaining())
                    outChannel.write(buffer);
                buffer.clear();
            }
            clientSocketChannel.close();
            outChannel.close();

            clientSocketChannel = createChannel();
            startEditUI();
        }
        if(risultato == 1){     //Caso sezione gia in modifica
            JOptionPane.showMessageDialog(null, "Sezione gia in modifica", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        if(risultato == 2){     //Caso utente non autorizzato alla modifica del file
            JOptionPane.showMessageDialog(null, "Non sei autorizzato a modificare questo file", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        if(risultato == 3){     //Caso numero di sezione non valido, piu grande del numero max di sezione del documento
            JOptionPane.showMessageDialog(null, "Numero di sezione non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        if(risultato == 4){     //Caso impossibile acquisire la lock sulla sezione
            JOptionPane.showMessageDialog(null, "Impossibile acquisire la lock sulla sezione del documento", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
    }
    //--------------------------------------   END EDIT DOCUMENTO   --------------------------------------
    private void endEdit() throws IOException {
        //Salvo tutto quello che era nella sectionArea
        String testoSezione = sectionArea.getText();
        //Svuoto la sectionArea
        sectionArea.setText("");
        //Scrivo tutto quello che era nella sectionArea nella sezione di file
        try (PrintWriter out = new PrintWriter(nomeSezioneModificata)) {
            out.write(testoSezione);
            out.flush();
        }catch (FileNotFoundException e){
            JOptionPane.showMessageDialog(null, "FILE non trovato", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return;
        }

        invioAlServer.write(4);     // Invio il comando 4 per end edit
        invioAlServer.writeBytes(nomeDocumento + '\n');
        invioAlServer.write(sezione);

        //Invio il file modificato con NIO
        FileChannel inChannel = FileChannel.open(Paths.get(nomeSezioneModificata), StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);

        boolean stop = false;

        while (!stop) {
            int bytesRead = inChannel.read(buffer);
            if (bytesRead == -1)
                stop = true;
            buffer.flip();
            while (buffer.hasRemaining())
                clientSocketChannel.write(buffer);
            buffer.clear();
        }
        clientSocketChannel.close();
        inChannel.close();

        //Risultato puo essere solo 0, serve per dare il via alla rimozione dei componenti di edit e chat dalla
        // interfaccia
        int risultato = ricevoDalServer.read();
        if(risultato == 0){
            sectionArea.setVisible(false);
            msgArea.setVisible(false);
            chatArea.setVisible(false);
            inviaMessaggio.setVisible(false);
            this.repaint();

            chat.closeChat();
            //Manda un messaggio di disconnessione alla chat in automatico
            disconnessioneChat();
        }
        //In modo da poter inviare altre richieste al server
        clientSocketChannel = createChannel();
    }
    //--------------------------------------   LISTA DOCUMENTI   --------------------------------------
    private void listaDocumentiAutorizzati() throws IOException {
        invioAlServer.write(5);     //Comando 5 per la visualizzazione della lista documenti
        invioAlServer.writeBytes(username + '\n');
        int dimensioneLista = ricevoDalServer.read();
        StringBuilder tmp = new StringBuilder();
        if(dimensioneLista == 0){
            tmp = new StringBuilder(ricevoDalServer.readLine());
            JOptionPane.showMessageDialog(null, tmp.toString());
        }
        else{
            for(int i = 0; i < dimensioneLista; i++){
                tmp.append(ricevoDalServer.readLine()).append('\n');
            }
            JOptionPane.showMessageDialog(null, "Lista documenti autorizzati: " + '\n' + tmp);
        }
    }
    //--------------------------------------   SHOW(S, D)   --------------------------------------
    private void showSection() throws IOException {
        String nomeDocumento;
        int sezione;
        //Creo una finestra con 2 campi di testo per inserire il nome del documento e il numero delle sezioni
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel label = new JPanel(new GridLayout(0, 1, 2, 2));
        label.add(new JLabel("Nome Documento", SwingConstants.RIGHT));
        label.add(new JLabel("Sezione", SwingConstants.RIGHT));
        panel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField nomeDocumentoField = new JTextField();
        controls.add(nomeDocumentoField);
        JTextField numeroSezioniField = new JTextField();
        controls.add(numeroSezioniField);
        panel.add(controls, BorderLayout.CENTER);
        //Controllo se viene premuto OK o CANCEL
        int input = JOptionPane.showConfirmDialog(null, panel, "SHOW(S, D)", JOptionPane.OK_CANCEL_OPTION);
        if(input == 0){//Caso OK
            nomeDocumento = nomeDocumentoField.getText();
            this.nomeDocumento = nomeDocumento;
            if (nomeDocumento == null || nomeDocumento.equals("")){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, nome documento inserito non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
            sezione = Integer.parseInt(numeroSezioniField.getText());
            //Controllo se il numero di sezione da modificare inserito e` valido
            if(sezione >= Configurazione.MAX_SECTIONS || sezione < 0){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, numero di sezione indicato non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        else return;
        //Invio al server il comando per scaricare la sezione di file e le informazioni necessarie
        invioAlServer.write(6);

        invioAlServer.writeBytes(username + '\n');
        invioAlServer.writeBytes(nomeDocumento + '\n');
        invioAlServer.write(sezione);

        int risultato = ricevoDalServer.read();
        //Risultato il numero di persone che stanno editando in questo momento la sezione di file
        if(risultato == 0 || risultato == 1){
            //Download della sezione di file richiesta, ed eventuale creazione delle deirectory necessarie
            File downloadDirectory = new File ("DOWNLOAD/");
            if(!downloadDirectory.exists()) downloadDirectory.mkdir();

            File subDirectory = new File("DOWNLOAD/" + nomeDocumento);
            if(!subDirectory.exists())	subDirectory.mkdir();

            String fileName = nomeDocumento + sezione + ".txt";
            File file = new File("DOWNLOAD/" + nomeDocumento, fileName);
            if(file.exists())file.delete();
            file.createNewFile();

            FileChannel fileChannel = FileChannel.open(Paths.get("DOWNLOAD/" + nomeDocumento + "/" + fileName),	StandardOpenOption.WRITE);
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);

            boolean stop = false;
            // NIO
            while(!stop) {
                int bytesRead = clientSocketChannel.read(buffer);
                if (bytesRead == -1) stop = true;
                buffer.flip();
                while (buffer.hasRemaining())
                    fileChannel.write(buffer);
                buffer.clear();
            }
            fileChannel.close();
            if(risultato == 0){
                JOptionPane.showMessageDialog(null, "Sezione di file correttamente scaricato, NESSUNO sta editando la sezione in questo momento");
            }
            else  JOptionPane.showMessageDialog(null, "Sezione di file correttamente scaricata, UN UTENTE sta modificando la sezione in questo momento");
        }
        if(risultato == 2){
            JOptionPane.showMessageDialog(null, "Il file richiesto non esiste", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if(risultato == 3){
            JOptionPane.showMessageDialog(null, "Il numero della sezione richiesta non e` valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if(risultato == 4){
            JOptionPane.showMessageDialog(null, "Utente non autorizzato", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        clientSocketChannel.close();
        clientSocketChannel = createChannel();
    }
    //--------------------------------------   SHOW(D)   --------------------------------------
    private void showDocument() throws IOException {
        String nomeDocumento;
        //Creo una finestra con 1 campo di testo per inserire il nome del documento
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel label = new JPanel(new GridLayout(0, 1, 2, 2));
        label.add(new JLabel("Nome Documento", SwingConstants.RIGHT));
        panel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField nomeDocumentoField = new JTextField();
        controls.add(nomeDocumentoField);
        panel.add(controls, BorderLayout.CENTER);
        //Controllo se viene premuto OK o CANCEL
        int input = JOptionPane.showConfirmDialog(null, panel, "SHOW(D)", JOptionPane.OK_CANCEL_OPTION);
        if(input == 0){     //Caso OK
            nomeDocumento = nomeDocumentoField.getText();
            this.nomeDocumento = nomeDocumento;
            if (nomeDocumento == null || nomeDocumento.equals("")){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, nome documento inserito non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        else return;
        //Invio al server il comando per scaricare l'intero documento e le informazioni necessarie
        invioAlServer.write(7);

        invioAlServer.writeBytes(username + '\n');
        invioAlServer.writeBytes(nomeDocumento + '\n');

        int risultato = ricevoDalServer.read();
        //Risultato e` il numero di utenti che stanno modificando le varie sezioni di file in questo momento
        if(risultato >= 0 && risultato < 30){
            //Download del file richiesto con NIO, ed eventuale creazione delle deirectory necessarie
            File downloadDirectory = new File ("DOWNLOAD/");				// Crea cartella Downloads se non esiste
            if(!downloadDirectory.exists()) downloadDirectory.mkdir();

            File subDirectory = new File("DOWNLOAD/" + nomeDocumento);
            if(!subDirectory.exists())	subDirectory.mkdir();

            String fileName = nomeDocumento + ".txt";
            File file = new File("DOWNLOAD/" + nomeDocumento, fileName);
            if(file.exists())file.delete();
            file.createNewFile();

            FileChannel outChannel = FileChannel.open(Paths.get("DOWNLOAD/" + nomeDocumento + "/" + fileName),	StandardOpenOption.WRITE);
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);

            boolean stop = false;
            // NIO
            while(!stop) {
                int bytesRead = clientSocketChannel.read(buffer);
                if (bytesRead == -1) stop = true;
                buffer.flip();
                while (buffer.hasRemaining())
                    outChannel.write(buffer);
                buffer.clear();
            }
            outChannel.close();
            if(risultato == 0){
                JOptionPane.showMessageDialog(null, "File correttamente scaricato, NESSUNO sta editando la sezione in questo momento");
            }
            else  JOptionPane.showMessageDialog(null, "File correttamente scaricata, " + risultato + " sta/stanno modificando attualmente le sezioni di file");
        }
        if(risultato == 30){
            JOptionPane.showMessageDialog(null, "Il file richiesto non esiste", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if(risultato == 31){
            JOptionPane.showMessageDialog(null, "Utente non autorizzato", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        clientSocketChannel.close();
        clientSocketChannel = createChannel();
    }
    //--------------------------------------   INVITE   --------------------------------------
    private void invitaUtente() throws IOException {
        String nomeDocumento, utenteDaInvitare;

        //Creo una finestra con 2 campi di testo per inserire il nome del documento e il numero delle sezioni
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel label = new JPanel(new GridLayout(0, 1, 2, 2));
        label.add(new JLabel("Nome Documento", SwingConstants.RIGHT));
        label.add(new JLabel("Utente da invitare", SwingConstants.RIGHT));
        panel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField nomeDocumentoField = new JTextField();
        controls.add(nomeDocumentoField);
        JTextField nomeUtenteDaInvitareField = new JTextField();
        controls.add(nomeUtenteDaInvitareField);
        panel.add(controls, BorderLayout.CENTER);
        //Controllo se viene premuto OK o CANCEL
        int input = JOptionPane.showConfirmDialog(null, panel, "SHOW(S, D)", JOptionPane.OK_CANCEL_OPTION);
        if(input == 0){//Caso OK
            nomeDocumento = nomeDocumentoField.getText();
            this.nomeDocumento = nomeDocumento;
            if (nomeDocumento == null || nomeDocumento.equals("")){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, nome documento inserito non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
            utenteDaInvitare = nomeUtenteDaInvitareField.getText();
            if (utenteDaInvitare == null || utenteDaInvitare.equals("")){
                JOptionPane.showMessageDialog(null, "ATTENZIONE, nome utente inserito non valido", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        else return;
        //Invio al server il comando e le informazioni necessarie
        invioAlServer.write(8);

        invioAlServer.writeBytes(username + '\n');
        invioAlServer.writeBytes(utenteDaInvitare + '\n');
        invioAlServer.writeBytes(nomeDocumento + '\n');

        int risultato = ricevoDalServer.read();
        if(risultato == 0){
            JOptionPane.showMessageDialog(null, "Utente " + utenteDaInvitare + " invitato");
        }
        if(risultato == 1){
            JOptionPane.showMessageDialog(null, "L'utente che si vuole invitare non e` registrato al servizio", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        if(risultato == 2){
            JOptionPane.showMessageDialog(null, "Il documento che si vuole autorizzare non esiste", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        if(risultato == 3){
            JOptionPane.showMessageDialog(null, "Solo il creatore puo invitare altri utenti", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
        if(risultato == 4){
            JOptionPane.showMessageDialog(null, "L'utente che si vuole invitare e` gia autorizzato alla modifica del documento", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
        }
    }

    private SocketChannel createChannel() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        int port = username.hashCode() % 65535;
        if(port < 0)
            port = -port % 65535;
        if(port < 1024)
            port += 1024;
        SocketAddress socketAddr = new InetSocketAddress("localhost", port);
        socketChannel.connect(socketAddr);
        return socketChannel;
    }

    private void inizializzazioneClient() {
        try {
            clientSocket = new Socket("localhost", Configurazione.PORT);
            invioAlServer = new DataOutputStream(clientSocket.getOutputStream());
            ricevoDalServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "ERRORE, server offline", "Server offline", JOptionPane.ERROR_MESSAGE);
        }
        this.msgList = new ArrayBlockingQueue<String>(50);
    }

    private void inizializzazioneUI() throws IOException {
        setLayout(null);			                        //Per gestire manualmente tutta l'interfaccia
        setSize(1100,700);                    //Definisce la dimensione della finestra

        usernameField = new JTextField("username");
        passwordField = new JPasswordField("password");

        statusLabel = new JLabel("OffLine");

        loginB = new JButton("Login");
        signUpB = new JButton("Sign In");
        logoutB = new JButton("Logout");
        invitaB = new JButton("Invita");
        creaTdocB = new JButton("Nuovo Documento");
        showSectionB = new JButton("Mostra Sezione");
        showDocumentB = new JButton("Mostra Documento");
        editB = new JButton("Modifica Documento");
        listB = new JButton("Lista Documenti");
        endEditB = new JButton("Fine Modifica");

        inviaMessaggio = new JButton("Invia Messaggio");

        chatArea = new JTextArea(400,280);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        sectionArea = new JTextArea();
        sectionArea.setEditable(true);
        sectionArea.setLineWrap(true);
        sectionArea.setWrapStyleWord(true);

        msgArea = new JTextArea();
        msgArea.setEditable(true);
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);

        posizionamentoComponenti();
        creaActionListener();

        add(usernameField);
        add(passwordField);
        add(statusLabel);
        add(signUpB);
        add(loginB);
        add(logoutB);
        add(invitaB);
        add(creaTdocB);
        add(showSectionB);
        add(showDocumentB);
        add(editB);
        add(listB);
        add(endEditB);
    }

    private void posizionamentoComponenti() {
        statusLabel.setBounds(450, 10, 90, 20);

        usernameField.setBounds(10, 10, 200, 20);
        usernameField.setColumns(10);
        passwordField.setBounds(220, 10, 200, 20);

        signUpB.setBounds(940, 10, 140, 30);
        loginB.setBounds(940, 60, 140, 30);
        logoutB.setBounds(940,110,140,30);
        creaTdocB.setBounds(940,160,140,30);
        invitaB.setBounds(940,210,140,30);
        showSectionB.setBounds(940,260,140,30);
        showDocumentB.setBounds(940, 310, 140, 30);
        listB.setBounds(940,360,140,30);
        editB.setBounds(940,410,140,30);
        endEditB.setBounds(940, 460, 140, 30);

        chatArea.setBounds(560, 40, 365, 290);
        sectionArea.setBounds(10, 40, 500, 500);
        msgArea.setBounds(560, 350, 365, 80);
        inviaMessaggio.setBounds(560, 450, 255, 30);

    }

    private void creaActionListener() {
        loginB.addActionListener(ae -> {
            //Effettuo un controllo sui valori inseriti in modo da non poter mandare dei valori di username e password
            //non validi al server
            if(controllaValoriInseriti())
                login();
        });
        signUpB.addActionListener(ae -> signUp());
        logoutB.addActionListener(ae -> logout());
        creaTdocB.addActionListener(ae -> creaDocumento());
        invitaB.addActionListener(ae -> {
            try {
                invitaUtente();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        showSectionB.addActionListener(ae -> {
            try {
                showSection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        showDocumentB.addActionListener(ae ->{
            try {
                showDocument();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listB.addActionListener(ae -> {
            try {
                listaDocumentiAutorizzati();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        editB.addActionListener(ae -> {
            try {
                edit();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        endEditB.addActionListener(ae ->{
            try {
                endEdit();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                //Invio il comando al server per eseguire l'operazione di chiusura
                try {
                    invioAlServer.write(9);
                    invioAlServer.writeBytes(username + '\n');
                    clientSocket.close();
                    if(clientSocketChannel != null)clientSocketChannel.close();
                    if(receiver != null)
                        receiver.stopReceiver();
                    System.out.println("--------     DISCONNESSO     --------");
                    System.exit(1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private boolean controllaValoriInseriti() {
        if(usernameField.getText().length() == 0) {
            JOptionPane.showMessageDialog(null, "Inserire username", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if(passwordField.getPassword().length == 0) {
            JOptionPane.showMessageDialog(null, "Inseriew password.", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private int controllaValoriDaRegistrare(String userN, String passW) {
        if(userN.length() == 0) {
            JOptionPane.showMessageDialog(null, "Inserisci un username.", "ERRORE", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        if(!userN.matches(Configurazione.VALID_CHARACTERS)) {
            JOptionPane.showMessageDialog(null, "Caratteri non validi nel campo username", "ERRORE", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        if(userN.length() > Configurazione.MAX_LENGTH) {
            JOptionPane.showMessageDialog(null, "Username troppo lungo", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        if(passW.length() == 0) {
            JOptionPane.showMessageDialog(null, "Inserisci una password.", "ERRORE", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        if(!passW.matches(Configurazione.VALID_CHARACTERS)) {
            JOptionPane.showMessageDialog(null, "Caratteri non validi nel campo password", "ERRORE", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        if(passW.length() > Configurazione.MAX_LENGTH) {
            JOptionPane.showMessageDialog(null, "Password troppo lunga", "ATTENZIONE", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return 0;
    }

    private void disconnessioneChat() throws IOException {
        calendar = Calendar.getInstance(TimeZone.getDefault());
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        String formatoMessaggio = "[" + username + " -- " + hour + ":" + minute + "] --> DISCONNESSO";
        byte[] m = formatoMessaggio.getBytes();
        DatagramPacket packet = new DatagramPacket(m, m.length, group, Configurazione.MULTICAST_PORT);
        chatSocket.send(packet);
        chatSocket.leaveGroup(group);
    }

    private void startEditUI() throws IOException {
        try {
            chatSocket = new MulticastSocket(Configurazione.MULTICAST_PORT);
            group = InetAddress.getByName(address);
        } catch (IOException e) {
            e.printStackTrace();
        }
        chat = new Chat(username, chatArea, chatSocket, group);
        chat.start();

        add(msgArea);
        add(chatArea);
        add(inviaMessaggio);
        add(sectionArea);

        inviaMsg("CONNESSO");

        sectionArea.setVisible(true);
        msgArea.setVisible(true);
        chatArea.setVisible(true);
        inviaMessaggio.setVisible(true);

        this.repaint();

        inviaMessaggio.addActionListener(ae -> inviaMsg(null));

        String lineaDiSezioneFile;
        FileReader fileReader = new FileReader(String.valueOf(Paths.get(nomeSezioneModificata)));
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        while((lineaDiSezioneFile = bufferedReader.readLine()) != null){
            sectionArea.append(lineaDiSezioneFile + '\n');
        }
        bufferedReader.close();
        fileReader.close();
    }

    private void inviaMsg(String messaggioOpzionale) {
        calendar = Calendar.getInstance(TimeZone.getDefault());
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        String formatoMessaggio;

        if(messaggioOpzionale != null && messaggioOpzionale.length() > 0){
            formatoMessaggio = "[" + username + " -- " + hour + ":" + minute + "] --> " + messaggioOpzionale;
            byte[] msgBytes = formatoMessaggio.getBytes();
            DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, group, Configurazione.MULTICAST_PORT);
            try {
                chatSocket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            String messaggio = msgArea.getText();
            formatoMessaggio = "[" + username + " -- " + hour + ":" + minute + "] --> " + messaggio;

            if(messaggio.length() > 0) {
                byte[] msgBytes = formatoMessaggio.getBytes();
                DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, group, Configurazione.MULTICAST_PORT);
                try {
                    chatSocket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            msgArea.setText("");
        }
    }
    private int generaPorta() {
        int port = username.hashCode() % 65535;
        if(port < 0)
            port = -port % 65535;
        if(port < 1024)
            port += 1024;
        return port + 300;
    }
}
