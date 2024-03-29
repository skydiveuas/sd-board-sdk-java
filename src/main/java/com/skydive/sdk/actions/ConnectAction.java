package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.CommMessage;
import com.skydive.sdk.CommTask;
import com.skydive.sdk.UavEvent;
import com.skydive.sdk.data.CalibrationSettings;
import com.skydive.sdk.data.SignalData;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.SignalPayloadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Bartosz Nawrot on 2016-10-13.
 */
public class ConnectAction extends CommHandlerAction {

    private static Logger logger = LoggerFactory.getLogger(ConnectAction.class);

    private static final long CONNECTION_TIMEOUT = 5000;

    public enum ConnectState {
        IDLE,
        INITIAL_COMMAND,
        PROTOCOL_VERSION,
        WAITING_FOR_CALIBRATION,
        WAITING_FOR_CALIBRATION_DATA,
        FINAL_COMMAND,
    }

    private ConnectState state;

    private boolean connectionProcedureDone;

    private CommTask connectionTimeoutTask;

    public ConnectAction(CommHandler commHandler) {
        super(commHandler);
        state = ConnectState.IDLE;
        connectionProcedureDone = false;

        connectionTimeoutTask = new CommTask(1000.0 / CONNECTION_TIMEOUT) {
            @Override
            protected String getTaskName() {
                return "connection_timeout_task";
            }

            @Override
            protected void task() {
                if (state == ConnectState.INITIAL_COMMAND) {
                    ConnectAction.this.commHandler.getUavManager().notifyUavEvent(
                            new UavEvent(UavEvent.Type.ERROR,
                                    "Timeout waiting for initial command response."));
                }
            }
        };
    }

    @Override
    public boolean isActionDone() {
        return connectionProcedureDone;
    }

    @Override
    public void start() {
        logger.info("Starting connection procedure");
        connectionProcedureDone = false;
        state = ConnectState.INITIAL_COMMAND;
        commHandler.send(new SignalData(SignalData.Command.START_CMD, SignalData.Parameter.START).getMessage());
        commHandler.startCommTask(connectionTimeoutTask);
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        ConnectState actualState = state;
        switch (state) {
            case INITIAL_COMMAND:
                if (event.matchSignalData(
                        new SignalData(SignalData.Command.START_CMD, SignalData.Parameter.ACK))) {
                    commHandler.stopCommTask(connectionTimeoutTask);
                    state = ConnectState.PROTOCOL_VERSION;
                    logger.info("Initial command received successfully");
                } else {
                    logger.info("Unexpected event received at state " + state.toString());
                }
                break;

            case PROTOCOL_VERSION:
                SignalData protocolResponse = event.getSignalData();
                if (protocolResponse.getCommand() == SignalData.Command.PROTOCOL_VERSION_VALUE &&
                        protocolResponse.getParameterValue() == CommMessage.PROTOCOL_VERSION) {
                    commHandler.send(new SignalData(SignalData.Command.PROTOCOL_VERSION, SignalData.Parameter.ACK).getMessage());
                    state = ConnectState.WAITING_FOR_CALIBRATION;
                    logger.info("Protocol setup done");
                } else {
                    commHandler.send(new SignalData(SignalData.Command.PROTOCOL_VERSION, SignalData.Parameter.NOT_ALLOWED).getMessage());
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.ERROR, "Unsupported protocol version"));
                }
                break;

            case WAITING_FOR_CALIBRATION:
                if (event.matchSignalData(
                        new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.READY))) {
                    state = ConnectState.WAITING_FOR_CALIBRATION_DATA;
                    logger.info("Calibration done successfully, data ready");
                } else if (event.matchSignalData(
                        new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.NON_STATIC))) {
                    logger.info("Calibration non static");
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.CALIBRATION_NON_STATIC));
                } else {
                    logger.info("Unexpected event received at state " + state.toString());
                }
                break;

            case WAITING_FOR_CALIBRATION_DATA:
                if (event.getType() == CommEvent.EventType.SIGNAL_PAYLOAD_RECEIVED
                        && ((SignalPayloadEvent) event).getDataType() == SignalData.Command.CALIBRATION_SETTINGS_DATA) {
                    SignalPayloadEvent signalEvent = (SignalPayloadEvent) event;

                    CalibrationSettings calibrationSettings = (CalibrationSettings) signalEvent.getData();
                    if (calibrationSettings.isValid()) {
                        logger.info("Calibration settings received after adHoc calibration");
                        state = ConnectState.FINAL_COMMAND;
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.ACK).getMessage());
                        commHandler.getUavManager().setCalibrationSettings(calibrationSettings);
                        // send final start command
                        commHandler.send(new SignalData(SignalData.Command.APP_LOOP, SignalData.Parameter.START).getMessage());
                    } else {
                        logger.info("Calibration settings received but the data is invalid, responding with DATA_INVALID");
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.DATA_INVALID).getMessage());
                    }
                } else {
                    logger.info("Unexpected event received at state " + state.toString());
                }
                break;

            case FINAL_COMMAND:
                if (event.matchSignalData(
                        new SignalData(SignalData.Command.APP_LOOP, SignalData.Parameter.ACK))) {
                    connectionProcedureDone = true;
                    state = ConnectState.IDLE;
                    logger.info("Final command received successfully, connection procedure done");
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.CONNECTED));
                } else {
                    logger.info("Unexpected event received at state " + state.toString());
                }
                break;

            default:
                throw new Exception("Event: " + event.toString() + " received at unknown state");
        }
        if (actualState != state) {
            logger.info("HandleEvent done, transition: " + actualState.toString() + " -> " + state.toString());
        } else {
            logger.info("HandleEvent done, no state change");
        }
    }

    @Override
    public ActionType getActionType() {
        return ActionType.CONNECT;
    }
}