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

    fer.start();
    for (int i = 0; i < NUM_CARS; i++) {
      automobile[i].start();
    }
    ambulance.start();

    try {
      fer.join(); // wait for ferry to finish its crossings
    } catch (InterruptedException e) {
    }

    // Interrupt all threads
    for (int i = 0; i < NUM_CARS; i++) {
      automobile[i].interrupt();
    }
    ambulance.interrupt();
  }
}

// Auto Thread
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
    while (!isInterrupted() && fry.isRunning()) {
      try {
        sleep((int) (300 * Math.random()));
      } catch (InterruptedException e) {
        break;
      }

      if (!fry.isRunning())
        break;

      System.out.println("Auto " + id_auto + " arrives at port " + port);
      fry.boardAuto(port);

      if (!fry.isRunning() || isInterrupted())
        break;

      System.out.println("Auto " + id_auto + " boards on the ferry at port " + port);
      fry.addLoad();

      fry.waitForArrival();

      port = 1 - port;
      System.out.println("Auto " + id_auto + " disembarks from ferry at port " + port);
      fry.reduceLoad();
    }
    System.out.println("Auto " + id_auto + " terminated");
  }
}

// Ambulance Thread
class Ambulance extends Thread {
  private int port;
  private Ferry fry;

  public Ambulance(int prt, Ferry ferry) {
    this.port = prt;
    this.fry = ferry;
  }

  public void run() {
    while (!isInterrupted() && fry.isRunning()) {
      try {
        sleep((int) (1000 * Math.random()));
      } catch (InterruptedException e) {
        break;
      }

      if (!fry.isRunning())
        break;

      System.out.println("Ambulance arrives at port " + port);
      fry.boardAmbulance(port);

      if (!fry.isRunning() || isInterrupted())
        break;

      System.out.println("Ambulance boards the ferry at port " + port);
      fry.addLoad();

      fry.waitForArrival();

      port = 1 - port;
      System.out.println("Ambulance disembarks the ferry at port " + port);
      fry.reduceLoad();
    }
    System.out.println("Ambulance terminates.");
  }
}

// Ferry Thread
class Ferry extends Thread {
  private int port;
  private int load = 0;
  private int numCrossings;
  private volatile boolean running = true;

  private final Semaphore[] canBoard = { new Semaphore(0), new Semaphore(0) };
  private final Semaphore ferryReady = new Semaphore(0);
  private final Semaphore mutex = new Semaphore(1);

  private volatile boolean ambulanceOnBoard = false;
  private volatile boolean disembarking = false;

  public Ferry(int prt, int nbtours) {
    this.port = prt;
    this.numCrossings = nbtours;
  }

  public void run() {
    System.out.println("Start at port " + port + " with a load of " + load + " vehicles");

    for (int i = 0; i < numCrossings; i++) {
      allowBoarding();

      while (true) {
        try {
          Thread.sleep(50);
          mutex.acquire();
          if (load == Lab3.MAXLOAD || ambulanceOnBoard) {
            mutex.release();
            break;
          }
          mutex.release();
        } catch (InterruptedException e) {
          return;
        }
      }

      try {
        mutex.acquire();
        System.out.println("Departure from port " + port + " with a load of " + load + " vehicles");
        System.out.println("Crossing " + i + " with a load of " + load + " vehicles");
        mutex.release();
      } catch (InterruptedException e) {
      }

      disembarking = true;
      port = 1 - port;

      try {
        Thread.sleep((int) (100 * Math.random()));
      } catch (InterruptedException e) {
      }

      System.out.println("Ferry Arrives at port " + port + " with a load of " + load + " vehicles");

      ferryReady.release(load); // allow disembark

      while (true) {
        try {
          mutex.acquire();
          if (load == 0) {
            disembarking = false;
            ambulanceOnBoard = false;
            mutex.release();
            break;
          }
          mutex.release();
          Thread.sleep(20);
        } catch (InterruptedException e) {
          return;
        }
      }
    }

    running = false; // signal shutdown
    System.out.println("Ferry stopped.");
  }

  private void allowBoarding() {
    canBoard[port].release(10);
  }

  public void boardAuto(int p) {
    while (running) {
      try {
        canBoard[p].acquire();
        mutex.acquire();
        if (port == p && !disembarking && load < Lab3.MAXLOAD && !ambulanceOnBoard) {
          mutex.release();
          return;
        }
        mutex.release();
        Thread.sleep(30);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  public void boardAmbulance(int p) {
    while (running) {
      try {
        canBoard[p].acquire();
        mutex.acquire();
        if (port == p && !disembarking && load < Lab3.MAXLOAD) {
          ambulanceOnBoard = true;
          mutex.release();
          return;
        }
        mutex.release();
        Thread.sleep(30);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  public void waitForArrival() {
    try {
      ferryReady.acquire();
    } catch (InterruptedException e) {
      return;
    }
  }

  public void addLoad() {
    try {
      mutex.acquire();
      load++;
      mutex.release();
    } catch (InterruptedException e) {
    }
  }

  public void reduceLoad() {
    try {
      mutex.acquire();
      load--;
      mutex.release();
    } catch (InterruptedException e) {
    }
  }

  public boolean isRunning() {
    return running;
  }
}