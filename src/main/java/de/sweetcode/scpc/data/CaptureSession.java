package de.sweetcode.scpc.data;

import de.sweetcode.scpc.Main;
import de.sweetcode.scpc.crash.CrashDetector;
import de.sweetcode.scpc.crash.CrashReport;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The CaptureSession represents one session worth of data.
 */
public class CaptureSession {

    private final static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private long sessionId;
    private final boolean isArchived;

    private GPUInformation gpuInformation = new GPUInformation();
    private CPUInformation cpuInformation = new CPUInformation();
    private DiskInformation diskInformation = new DiskInformation(this);
    private OSInformation osInformation = new OSInformation();

    private GameInformation gameInformation = new GameInformation("", "");
    private CrashReport crashReport = new CrashReport();

    private List<DataPoint> dataPoints = new LinkedList<>();

    private Map<Class, List<Listener>> listeners = new HashMap<>();

    private CrashDetector crashDetector = new CrashDetector();
    private ScheduledFuture<?> scheduledTask;

    //--- Hardware
    private OSProcess process = null;
    private long deltaCPUTime = -1;
    private long lastCPUCheckTime = 0;

    /**
     * Creates a CaptureSession with the default id (-1) & archive.
     */
    public CaptureSession() {
        this(-1L, true);
    }

    /**
     * Creates a CaptureSession with the provided sessionId.
     * @param sessionId The session id the captured data belongs to.
     */
    public CaptureSession(long sessionId, boolean isArchived) {
        this.sessionId = sessionId;
        this.isArchived = isArchived;

        if(Main.FEATURE_CRASH_REPORT) {
            if (!(this.isArchived)) {
                this.scheduledTask = CaptureSession.executor.scheduleAtFixedRate(this.crashDetector, 0, 5, TimeUnit.SECONDS);
                this.crashDetector.addListener((crashReport) -> {
                    this.crashReport = crashReport;
                    this.scheduledTask.cancel(false);

                    if (this.crashReport.isGracefullyShutdown()) {
                        this.add(new DataPoint(GameStates.SHUTDOWN_GRACEFULLY, this.dataPoints.size()));
                    } else {
                        this.add(new DataPoint(GameStates.SHUTDOWN_CRASHED, this.dataPoints.size()));
                    }
                });
            }
        }

        if(Main.FEATURE_OSHI_HARDWARE_DETECTION && !isArchived) {
            this.cpuInformation.extractData();
            this.diskInformation.extractData();
            this.osInformation.extractData();
        }
    }

    /**
     * The associated session id.
     * @return -1, default value, otherwise > 0.
     */
    public long getSessionId() {
        return this.sessionId;
    }

    public OSProcess getProcess() {
        return this.process;
    }

    public long getDeltaCPUTime() {
        return this.deltaCPUTime;
    }

    public long getLastCPUCheckTime() {
        return this.lastCPUCheckTime;
    }

    public boolean isArchived() {
        return this.isArchived;
    }

    public DataPoint get(int index) {
        return this.dataPoints.get(index);
    }
    /**
     * All data points.
     * @return A LinkedList, never null, but can be empty.
     */
    public List<DataPoint> getDataPoints() {
        return this.dataPoints;
    }

    public GameInformation getGameInformation() {
        return this.gameInformation;
    }

    public GPUInformation getGPUInformation() {
        return this.gpuInformation;
    }

    public CPUInformation getCPUInformation() {
        return this.cpuInformation;
    }

    public DiskInformation getDiskInformation() {
        return this.diskInformation;
    }

    public OSInformation getOSInformation() {
        return this.osInformation;
    }

    public CrashReport getCrashReport() {
        return this.crashReport;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
        this.notifyListeners(this);
    }

    public void setDeltaCPUTime(long deltaCPUTime) {
        this.deltaCPUTime = deltaCPUTime;
    }

    public void setLastCPUCheckTime(long lastCPUCheckTime) {
        this.lastCPUCheckTime = lastCPUCheckTime;
    }

    public void setGPUInformation(GPUInformation gpuInformation) {
        this.gpuInformation = gpuInformation;
        this.notifyListeners(gpuInformation);
    }

    public void setCPUInformation(CPUInformation cpuInformation) {
        this.cpuInformation = cpuInformation;
        this.notifyListeners(gpuInformation);
    }

    public void setDiskInformation(DiskInformation diskInformation) {
        this.diskInformation = diskInformation;
        this.notifyListeners(diskInformation);
    }

    public void setGameInformation(GameInformation gameInformation) {
        this.gameInformation = gameInformation;
        this.notifyListeners(gameInformation);
    }

    public void setOSInformation(OSInformation osInformation) {
        this.osInformation = osInformation;
        this.notifyListeners(osInformation);
    }

    public void setCrashReport(CrashReport crashReport) {
        this.crashReport = crashReport;
    }

    /**
     * Adds a new data point and calls all associated listeners.
     * @param dataPoint The data point.
     */
    public void add(DataPoint dataPoint) {
        this.dataPoints.add(dataPoint);
        this.notifyListeners(dataPoint);
        this.notifyListeners(dataPoint.getGameState()); //:GameStateEvent
    }

    /**
     * Adds a listener, called when a new DataPoint gets added to the session.
     * @param listener
     */
    public <T> void addListener(Class<T> type, Listener<T> listener) {
        if(!(this.listeners.containsKey(type))) {
            this.listeners.put(type, new LinkedList<>());
        }
        this.listeners.get(type).add(listener);
    }

    private <T> void notifyListeners(T data) {

        Class clazz = data.getClass();

        //@HACKY: This is for enums that implement an interface like (GameStates). :GameStateEvent
        if(data instanceof Enum<?>) {
            clazz = data.getClass().getSuperclass().getInterfaces()[0];
        }

        if(this.listeners.containsKey(clazz)) {
            this.listeners.get(clazz).forEach(e -> e.captured(data));
        }
    }

    public void updateProcess() {
        OSProcess[] processes = Main.getSystemInfo().getOperatingSystem().getProcesses(Integer.MAX_VALUE, OperatingSystem.ProcessSort.CPU);
        for(OSProcess process : processes) {
            if(process.getName().equalsIgnoreCase("StarCitizen") || process.getName().equalsIgnoreCase("starcitizen.exe")) {
                this.process = process;
                break;
            }
        }
    }

    public interface Listener<T> {
        void captured(T data);
    }

}
