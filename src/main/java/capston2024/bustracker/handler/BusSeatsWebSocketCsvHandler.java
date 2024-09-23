package capston2024.bustracker.handler;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class BusSeatsWebSocketCsvHandler extends TextWebSocketHandler {
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String csvData = message.getPayload();

        // CSV 파일 저장
        saveCsvData(csvData);

        // 클라이언트에 응답 메시지 전송
        session.sendMessage(new TextMessage("CSV 좌석 데이터 수신이 성공적으로 완료되었습니다."));
    }

    private void saveCsvData(String csvData) {
        String filePath = "sensor_seats_data.csv"; // 저장할 파일 경로
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(csvData);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    private Bus createBusFormSeatDTO()
}
