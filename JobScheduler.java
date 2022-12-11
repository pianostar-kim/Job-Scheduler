// https://stackoverflow.com/questions/4691533/java-wait-for-thread-to-finish
// https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/Condition.html
// https://www.baeldung.com/java-mutex
// https://docs.oracle.com/javase/10/docs/api/java/util/concurrent/locks/Condition.html
// https://docs.oracle.com/javase/10/docs/api/java/util/concurrent/locks/ReentrantLock.html
// https://www.baeldung.com/java-stop-execution-after-certain-time

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Scanner;

public class JobScheduler {
   public static final int FCFS_MODE = 1;
   public static final int SJF_MODE = 2;
   public static final int PRIORITY_MODE = 3;

   private static Job[] queue = new Job[10];
   private static int head = 0;
   private static int tail = 0;
   private static int jobsInQueue = 0;
   private static ReentrantLock mutex = new ReentrantLock();
   private static Condition notFull = mutex.newCondition();
   private static Condition notEmpty = mutex.newCondition();
   private static int orderNo = 1;
   private static int currentMode = FCFS_MODE;
   private static int jobsSubmitted = 0;
   private static double totalTurnaroundTime = 0;
   private static double totalCPUTime = 0;
   private static double totalWaitingTime = 0;

   public static void main(String[] args) {
      System.out.println("Welcome to pianostar-kim's job scheduler! Here are the following commands you will need to know about.");
      System.out.println("A - Add a new job");
      System.out.println("F - Switch to FCFS mode (this is the default mode)");
      System.out.println("S - Switch to SJF mode");
      System.out.println("P - Switch to priority mode");
      System.out.println("M - Get the current scheduling mode");
      System.out.println("Q - Quit (any jobs that are still in the queue will get executed before application ends)");
      Thread scheduler = new Thread(
         new Runnable() {
            public void run() {
               schedule();
            }
         });
      Thread executer = new Thread(
         new Runnable() {
            public void run() {
               execute();
            }
         });
      scheduler.start();
      executer.start();
      try {
         scheduler.join();
         System.out.println("Waiting for jobs still in queue to be executed...");
         executer.join();
         DecimalFormat formatter = new DecimalFormat("#,##0.####");
         System.out.println("Total number of jobs submitted: " + jobsSubmitted);
         System.out.println("Average turnaround time: " + (jobsSubmitted > 0 ? formatter.format((totalTurnaroundTime / jobsSubmitted)) : formatter.format(jobsSubmitted)) + " seconds");
         System.out.println("Average CPU time:        " + (jobsSubmitted > 0 ? formatter.format((totalCPUTime / jobsSubmitted)) : formatter.format(jobsSubmitted)) + " seconds");
         System.out.println("Average waiting time:    " + (jobsSubmitted > 0 ? formatter.format((totalWaitingTime / jobsSubmitted)) : formatter.format(jobsSubmitted)) + " seconds");
      }
      catch (InterruptedException e) {
         System.out.println("An error occurred while waiting for both threads to finish.");
      }
   }
   
   private static void schedule() {
      char commandLetter = 'B';
      Scanner scanner = new Scanner(System.in);
      do {
         System.out.print("Enter a command: ");
         String input = scanner.nextLine();
         if (input.length() == 0) { 
            continue; }
         commandLetter = input.toUpperCase().charAt(0);
         switch (commandLetter) {
            case 'A':     // COMPLETE
               int timeToRun = 0;
               int priorityValue = 0;
               System.out.print("Enter a positive integer for how long (in seconds) this job will take: ");
               boolean properInputEntered = false;
               while (!properInputEntered) {
                  try {
                     timeToRun = Integer.parseInt(scanner.nextLine());
                     if (timeToRun > 0) { properInputEntered = true; }
                     else { System.out.print("Invalid input. Please enter a positive integer for how long (in seconds) this job will take: "); }
                  }
                  catch (NumberFormatException e) {
                     System.out.print("Invalid input. Please enter a positive integer for how long (in seconds) this job will take: ");
                  }
               }
               System.out.print("Enter an integer for this job's priority value (a lower number for priority = higher priority): ");
               properInputEntered = false;
               while (!properInputEntered) {
                  try {
                     priorityValue = Integer.parseInt(scanner.nextLine());
                     properInputEntered = true;
                  }
                  catch (NumberFormatException e) {
                     System.out.print("Invalid input. Please enter an integer for this job's priority value (a lower number for priority = higher priority): ");
                  }
               }
               Job jobToAdd = new Job(timeToRun, orderNo++, priorityValue);
               mutex.lock();
               while (jobsInQueue == queue.length) {
                  System.out.println("Job queue is currently full. Please wait..."); 
                  notFull.awaitUninterruptibly();
               }
               jobToAdd.setQueueArrivalTime(System.currentTimeMillis());
               queue[tail++] = jobToAdd;
               jobsInQueue++;
               if (currentMode == FCFS_MODE) { reorderByOrder(); }
               else if (currentMode == SJF_MODE) { reorderByTime(); }
               else { reorderByPriority(); }     // current mode is priority mode
               jobsSubmitted++;
               System.out.println("Job added.");
               notEmpty.signal();
               if (tail == queue.length) { tail = 0; }
               mutex.unlock();
               break;
            case 'F':     // COMPLETE
               System.out.println("Waiting for next job to be fetched so resorting can take place safely...");
               mutex.lock();
               reorderByOrder();
               currentMode = FCFS_MODE;
               System.out.println("You are now in FCFS mode.");
               mutex.unlock();
               break;
            case 'S':     // COMPLETE
               System.out.println("Waiting for next job to be fetched so resorting can take place safely...");
               mutex.lock();
               reorderByTime();
               currentMode = SJF_MODE;
               System.out.println("You are now in SJF mode.");
               mutex.unlock();
               break;
            case 'P':     // COMPLETE
               System.out.println("Waiting for next job to fetched so resorting can take place safely...");
               mutex.lock();
               reorderByPriority();
               currentMode = PRIORITY_MODE;
               System.out.println("You are now in priority mode.");
               mutex.unlock();
               break;
            case 'M':     // COMPLETE
               switch (currentMode) {
                  case FCFS_MODE:
                     System.out.println("You are currently in FCFS mode.");
                     break;
                  case SJF_MODE:
                     System.out.println("You are currently in SJF mode.");
                     break;
                  default:
                     System.out.println("You are currently in priority mode.");
               }
               break;
            case 'Q':     // COMPLETE
               // will submit a dummy job that lets the executer thread to stop since user has chosen to quit application
               mutex.lock();
               while (jobsInQueue == queue.length) {
                  System.out.println("Waiting for jobs still in queue to be executed...");
                  notFull.awaitUninterruptibly();
               }
               queue[tail++] = new Job(0, 0, 0);
               jobsInQueue++;
               notEmpty.signal();
               if (tail == queue.length) { tail = 0; }
               mutex.unlock();
               break;
            default:     // COMPLETE
               System.out.println("Invalid command entered.");
         }
      } while (commandLetter != 'Q');
   }
   
   private static void execute() {
      while (true) {
         mutex.lock();
         while (jobsInQueue == 0) { notEmpty.awaitUninterruptibly(); }
         Job currentJob = queue[head++];
         int jobTime = currentJob.getSecToRun();
         long queueArrivalTime = currentJob.getQueueArrivalTime();
         jobsInQueue--;
         notFull.signal();
         if (head == queue.length) { head = 0; }
         mutex.unlock();
         if (jobTime == 0) { 
            break; }     // when executer encounters dummy job with job time of 0 seconds, it will quit
         long start = System.currentTimeMillis();
         performJob(jobTime);
         long finish = System.currentTimeMillis();
         totalTurnaroundTime += (((double) (finish - queueArrivalTime)) / 1000);
         totalCPUTime += (((double) (finish - start)) / 1000);
         totalWaitingTime += (((double) (start - queueArrivalTime)) / 1000);
      }
   }
   
   private static void performJob(int jobTimeInSec) {
      int result = 0;
      long endTime = System.currentTimeMillis() + jobTimeInSec * 1000;
      while (System.currentTimeMillis() < endTime) {
         result += 1;
         if (result == Integer.MAX_VALUE) { result = 0; }
      }
   }
   
   private static void reorderByOrder() {
      Job[] copyOfRemainingJobs = new Job[jobsInQueue];
      int j = head;
      for (int i = 0; i < copyOfRemainingJobs.length; i++) {
         copyOfRemainingJobs[i] = queue[j];
         j++;
         if (j == queue.length) { j = 0; }
      }
      Arrays.sort(copyOfRemainingJobs);
      j = head;
      for (int i = 0; i < copyOfRemainingJobs.length; i++) {
         queue[j] = copyOfRemainingJobs[i];
         j++;
         if (j == queue.length) { j = 0; }
      }
   }
   
   private static void reorderByTime() {
      Job[] copyOfRemainingJobs = new Job[jobsInQueue];
      int j = head;
      for (int i = 0; i < copyOfRemainingJobs.length; i++) {
         copyOfRemainingJobs[i] = queue[j];
         j++;
         if (j == queue.length) { j = 0; }
      }
      Arrays.sort(copyOfRemainingJobs, new TimeComparator());
      j = head;
      for (int i = 0; i < copyOfRemainingJobs.length; i++) {
         queue[j] = copyOfRemainingJobs[i];
         j++;
         if (j == queue.length) { j = 0; }
      }
   }
   
   private static void reorderByPriority() {
      Job[] copyOfRemainingJobs = new Job[jobsInQueue];
      int j = head;
      for (int i = 0; i < copyOfRemainingJobs.length; i++) {
         copyOfRemainingJobs[i] = queue[j];
         j++;
         if (j == queue.length) { j = 0; }
      }
      Arrays.sort(copyOfRemainingJobs, new PriorityComparator());
      j = head;
      for (int i = 0; i < copyOfRemainingJobs.length; i++) {
         queue[j] = copyOfRemainingJobs[i];
         j++;
         if (j == queue.length) { j = 0; }
      }
   }
   
   static class Job implements Comparable<Job> {
      private int secToRun;
      private int order;
      private int priority;
      private long queueArrivalTime = 0;
      
      public Job(int secToRunIn, int orderIn, int priorityIn) {
         secToRun = secToRunIn;
         order = orderIn;
         priority = priorityIn;
      }
      
      public int getSecToRun() { 
         return secToRun; }
         
      public int getOrder() { 
         return order; }
         
      public int getPriority() {
         return priority;
      }
      
      public long getQueueArrivalTime() { 
         return queueArrivalTime; }
      
      public void setQueueArrivalTime(long queueArrivalTimeIn) {
         queueArrivalTime = queueArrivalTimeIn;
      }
      
      public int compareTo(Job jobToCompare) {
         if (this.order == jobToCompare.getOrder()) { 
            return 0; }
         else if (this.order > jobToCompare.getOrder()) { 
            return 1; }
         else { 
            return -1; }
      }
   }
   
   static class TimeComparator implements Comparator<Job> {
      public int compare(Job job1, Job job2) {
         if (job1.getSecToRun() == job2.getSecToRun()) { 
            return 0; }
         else if (job1.getSecToRun() > job2.getSecToRun()) { 
            return 1; }
         else { 
            return -1; }
      }
   }
   
   static class PriorityComparator implements Comparator<Job> {
      public int compare(Job job1, Job job2) {
         if (job1.getPriority() == job2.getPriority()) { 
            return 0; }
         else if (job1.getPriority() > job2.getPriority()) { 
            return 1; }
         else { 
            return -1; }
      }
   }
}