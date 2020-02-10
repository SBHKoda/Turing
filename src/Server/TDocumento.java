package Server;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class TDocumento {
    private String docName;
    private String creatorName;
    private int sections;

    private CopyOnWriteArrayList<String> listaUtentiAutorizzati;
    private ArrayList<ReentrantLock> reentrantLocks;

    private InetAddress multicastAddress;

    public TDocumento(String creatorName, String docName, int sections, InetAddress multicastAddress){
        this.docName = docName;
        this.creatorName = creatorName;
        this.sections = sections;
        this.multicastAddress = multicastAddress;

        this.listaUtentiAutorizzati = new CopyOnWriteArrayList<>();

        this.reentrantLocks = new ArrayList<>(sections);
        for (int i = 0; i < sections; i++)
            reentrantLocks.add(i, new ReentrantLock(true));
    }

    public CopyOnWriteArrayList<String> getListaAutorizzati(){
        return this.listaUtentiAutorizzati;
    }
    public void addListaAutorizzati(String username){
        this.listaUtentiAutorizzati.add(username);
    }
    public int getSections(){
        return this.sections;
    }
    public boolean isCreatore(String username){
        return this.creatorName.equals(username);
    }
    public synchronized boolean isLocked(int sectionNumber){
        return this.reentrantLocks.get(sectionNumber).isLocked();
    }
    public synchronized boolean lockSection(int sectionNumber){
        return this.reentrantLocks.get(sectionNumber).tryLock();
    }
    public synchronized void unlockSection(int sectionNumber){
        this.reentrantLocks.get(sectionNumber).unlock();
    }
    public synchronized InetAddress getMulticastAddress(){
        return this.multicastAddress;
    }
}
