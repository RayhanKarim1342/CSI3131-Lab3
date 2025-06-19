import java.util.Random;
import java.util.concurrent.Semaphore;

public class Lab3 {
  final static int PORT0 = 0;
  final static int PORT1 = 1;
  final static int MAXLOAD = 5;

  public static void main(String args[]) {
    final int NUM_CARS = 10;

    Ferry fer = new Ferry(PORT0, 10);

    Auto[] automobile = new Auto[NUM_CARS];
    for (int i = 0; i < 7; i++) {
      automobile[i] = new Auto(i, PORT0, fer);
    }
    for (int i = 7; i < NUM_CARS; i++) {
      automobile[i] = new Auto(i, PORT1, fer);
    }

    Ambulance ambulance = new Ambulance(PORT0, fer);

    fer.start(); // Start ferry thread
    for (int i = 0; i < NUM_CARS; i++) {
      automobile[i].start(); // Start automobile threads
    }
    ambulance.start(); // Start ambulance thread

    try {
      fer.join();
    } catch (InterruptedException e) {
    }

    System.out.println("Ferry stopped.");
    for (int i = 0; i < NUM_CARS; i++) {
      automobile[i].interrupt();
    }
    ambulance.interrupt();
  }
}

class Auto extends Thread {
  private int id_auto;
  private int port;
  private Ferry fry;

  public Auto(int id, int prt, Ferry ferry) {
    this.id_auto = id;
    this.port = prt;
    this.fry = ferry;
  }

  public void run() {
    while (true) {
      try {
        sleep((int) (300 * Math.random()));
        System.out.println("Auto " + id_auto + " arrives at port " + port);
        boolean boarded = false;
        while (!boarded) {
          if (fry.getPort() == port && !fry.isUnloading()) {
            if (fry.canBoard.tryAcquire()) {
              System.out.println("Auto " + id_auto + " boards on the ferry at port " + port);
              fry.addLoad(false);
              boarded = true;
            } else {
              sleep(50); // no permit yet
            }
          } else {
            sleep(50); // ferry not ready
          }
        }

        sleep(100); // simulate travel
        port = 1 - port;

        fry.canDisembark.acquire();
        System.out.println("Auto " + id_auto + " disembarks from ferry at port " + port);
        fry.reduceLoad();

        if (isInterrupted())
          break;

      } catch (InterruptedException e) {
        break;
      }
    }
    System.out.println("Auto " + id_auto + " terminated");
  }
}

class Ambulance extends Thread {
  private int port;
  private Ferry fry;

  public Ambulance(int prt, Ferry ferry) {
    this.port = prt;
    this.fry = ferry;
  }

  public void run() {
    while (true) {
      try {
        sleep((int) (1000 * Math.random()));
        System.out.println("Ambulance arrives at port " + port);

        boolean boarded = false;
        while (!boarded) {
          if (fry.getPort() == port && !fry.isUnloading()) {
            if (fry.canBoard.tryAcquire()) {
              System.out.println("Ambulance boards the ferry at port " + port);
              fry.addLoad(true);
              boarded = true;
            } else {
              sleep(50);
            }
          } else {
            sleep(50);
          }
        }

        sleep(100); // simulate travel
        port = 1 - port;

        fry.canDisembark.acquire();
        System.out.println("Ambulance disembarks from ferry at port " + port);
        fry.reduceLoad();

        if (isInterrupted())
          break;

      } catch (InterruptedException e) {
        break;
      }
    }
    System.out.println("Ambulance terminates.");
  }
}

class Ferry extends Thread {
  private int port;
  private int load = 0;
  private int numCrossings;

  final static int MAXLOAD = 5;

  Semaphore mutex = new Semaphore(1);
  Semaphore canBoard = new Semaphore(0);
  Semaphore canDisembark = new Semaphore(0);
  Semaphore ferryReady = new Semaphore(0);
  Semaphore crossing = new Semaphore(0);

  private boolean unloading = false;
  private boolean ambulanceBoarded = false;

  public Ferry(int prt, int nbtours) {
    this.port = prt;
    this.numCrossings = nbtours;
  }

  public void run() {
    System.out.println("Start at port " + port + " with a load of " + load + " vehicles");

    for (int i = 0; i < numCrossings; i++) {
      try {
        // Disembark phase
        mutex.acquire();
        unloading = true;
        mutex.release();

        canDisembark.release(MAXLOAD);
        Thread.sleep(100); // give time for disembarking

        mutex.acquire();
        load = 0;
        unloading = false;
        mutex.release();

        // Boarding phase
        canBoard.release(MAXLOAD);

        // Wait for ferry to be full or ambulance to board
        ferryReady.acquire();

        System.out.println("Departure from port " + port + " with a load of " + load + " vehicles");
        Thread.sleep(100); // simulate travel
        port = 1 - port;
        System.out.println("Arrive at port " + port + " with a load of " + load + " vehicles");

      } catch (InterruptedException e) {
        break;
      }
    }
  }

  public int getPort() {
    return port;
  }

  public boolean isUnloading() {
    return unloading;
  }

  public void addLoad(boolean isAmbulance) {
    try {
      mutex.acquire();
      load++;
      if (isAmbulance) {
        ambulanceBoarded = true;
        ferryReady.release(); // leave immediately
      } else if (load == MAXLOAD) {
        ferryReady.release(); // leave if full
      }
      mutex.release();
    } catch (InterruptedException e) {
    }
  }

  public void reduceLoad() {
    try {
      mutex.acquire();
      load--;
      if (load == 0 && ambulanceBoarded) {
        ambulanceBoarded = false;
      }
      mutex.release();
    } catch (InterruptedException e) {
    }
  }
}
