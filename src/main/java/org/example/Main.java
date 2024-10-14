package org.example;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;

public class Main {
    public static void main(String[] args) {
        Board board = new Board();
        Cell cell = board.getCell(0, 0);
        Robot robot = new Robot(cell, board);
        InputOutput io = new InputOutput();
        int cnt = 0;
        int waitTo = 0;
        Scanner in = new Scanner(System.in);
        Client client = new Client();
        SensorData prev = client.getData();
        while (board.visitedCells != board.totalCells){
            SensorData date = client.getData();
            boolean isStepped = robot.makeStep();

            io.showBoard(board, date, prev, robot);
            prev = date;
//            if(cnt == waitTo){
//                cnt--;
//                int next = in.nextInt();
//                waitTo += next;
//            }
//            cnt++;
//            System.out.println(cnt);
            System.out.println(board.visitedCells + " " + board.totalCells);
            if(!isStepped){
                robot.returnBackToСrossroad();
            }
        }
        SensorData date = client.getData();
        io.showBoard(board, date, prev, robot);
        System.out.println(board.visitedCells + " " + board.totalCells);

    }


}


class Robot{
    Cell curCell = null;
    Board board;
    int curDirection = 0;
    Client client = new Client();
    double BORDER_TO_GO = 130;
    boolean isCentered = true;
    double diffX = 0;
    double diffY = 0;
    Stack<Integer> memory = new Stack<>();
    List<MoveAction> actions = Arrays.asList(
            this::goForward,
            this::goRight,
            this::goBack,
            this::goLeft
    );

    public Robot(Cell cell, Board board){
        curCell = cell;
        board.visitCell(cell);
        this.board = board;
    }

    public boolean makeStep(){
        SensorData data = client.getData();
        double[] dist = new double[]{data.front_distance(), data.right_side_distance(), data.back_distance(), data.left_side_distance()};

        checkIsCentered(data);
        analyzeData(dist);

        for(int i = 0;i<4;i++){
            if(canGo(dist[i], i)){
                memory.add(i);
                curCell = getCell(i);
                board.visitCell(curCell);
                actions.get(i).execute();
                return true;
            }
        }
        return false;
    }

    public void returnBackToСrossroad(){
        this.turnAround();
        memory.add(0);
        while(true){
            SensorData data = client.getData();
            double[] dist = new double[]{data.front_distance(), data.right_side_distance(), data.back_distance(), data.left_side_distance()};
            //TODO
            boolean fl = false;
            for(int i = 0;i<4;i++){
                if(canGo(dist[i], i)){
                    fl=true;
                }
            }
            if(fl)break;
            int move = memory.pop();
            move = move == 0 ? move : (move+2)%4;
            curCell = getCell(move);
            actions.get(move).execute();
        }
        int lastMove = memory.pop();
        switch (lastMove){
            case 0:
                this.turnAround();
                break;
            case 1:
                client.turnRight();
                curDirection = (curDirection + 1) % 4;
                break;
            case 4:
                client.turnLeft();
                curDirection = (curDirection + 3) % 4;
        }
    }

    public Cell getCell(int direction){
        int[][] dydx = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int[] curDD = dydx[(direction+curDirection) % 4];
        return board.getCell(curCell.y + curDD[0], curCell.x + curDD[1]);
    }

    public boolean canGo(double dist, int direction){
        if(dist <= BORDER_TO_GO){
            return false;
        }
        Cell nextCell = getCell(direction);
        return !nextCell.visited;
    }

    public void analyzeData(double[] dist){
        for(int i = 0; i<4;i++){
            switch ((curDirection+i) % 4){
                case 0 -> curCell.setTopWall(dist[i] <= BORDER_TO_GO);
                case 1 -> curCell.setRightWall(dist[i] <= BORDER_TO_GO);
                case 2 -> curCell.setBottomWall(dist[i] <= BORDER_TO_GO);
                case 3 -> curCell.setLeftWall(dist[i] <= BORDER_TO_GO);
            }
        }
    }

    public void checkIsCentered(SensorData sensorData){
        double xWall = 166.7;
        double yWall = 167;
        double boardX = curCell.x - 8 + 0.5;
        double boardY = curCell.y - 8 + 0.5;
        double curX = sensorData.down_y_offset();
        double curY = sensorData.down_x_offset();
        double shouldBeX = xWall * boardX;
        double shouldBeY = yWall * boardY;
        this.diffX = Math.abs(curX - shouldBeX);
        this.diffY = Math.abs(curY - shouldBeY);
        this.isCentered = Math.abs(curX - shouldBeX) <= 25 && Math.abs(curY - shouldBeY) <= 25;
    }

    public void goRight(){
//        System.out.println("moveRight");
        curDirection = (curDirection + 1) % 4;
        client.turnRight();
        client.goForward();
    }

    public void goLeft(){
        curDirection = (curDirection- 1 + 4) % 4;
//        System.out.println("moveLeft");
        client.turnLeft();
        client.goForward();
    }

    public void turnAround(){
        curDirection = (curDirection + 2) % 4;
        client.turnLeft();
        client.turnLeft();
    }

    public void goForward(){
//        System.out.println("goForward");
        client.goForward();
    }

    public void goBack(){
//        System.out.println("goBack");
        client.goBack();
    }

}

class Board{
    int totalCells = 16*16;
    int visitedCells = 0;
    List<List<Cell>> matrix = new ArrayList<>();
    public Board(){
        for(int i = 15; i>-1;i--){
            List<Cell> temp = new ArrayList<>();
            for(int j = 0; j<16;j++){
                temp.add(new Cell(i, j));
            }
            matrix.add(temp);
        }
    }

    public Cell getCell(int y, int x){
        return matrix.get(15-y).get(x);
    }

    public void visitCell(Cell cell){
        cell.visited = true;
        visitedCells++;
    }

}

class Cell{
    int x;
    int y;
    @Setter
    Boolean topWall = null;
    @Setter
    Boolean leftWall = null;
    @Setter
    Boolean rightWall = null;
    @Setter
    Boolean bottomWall = null;
    @Setter
    Boolean visited = false;

    public Cell(int y, int x){
        this.x = x;
        this.y = y;
    }
}

class Client{
    public static String baseUri = "http://127.0.0.1:8801/api/v1/robot-cells/";
    public static String token = "0218a97c-38a3-42e8-a97d-08c565ee7d95e80ba549-6ee3-4070-a18b-6b4bdd87aea0";
    public HttpClient client = HttpClient.newHttpClient();

    public static String getUri(String uri){
        return baseUri + uri + "?token=" + token;
    }

    public Client(){
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUri + "sensor-data?" + "token=" + token))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper objectMapper = new ObjectMapper();
            SensorData sensorData = objectMapper.readValue(response.body(), SensorData.class);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void goForward(){
        makeAction("forward");
    }

    public void goBack(){
        makeAction("backward");
    }

    public void turnLeft(){
        makeAction("left");
    }

    public void turnRight(){
        makeAction("right");
    }

    public void makeAction(String action){
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getUri(action)))
                    .POST(HttpRequest.BodyPublishers.ofString("", StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            try{
                Thread.sleep(250);
            } catch (InterruptedException e){
                e.printStackTrace();
            }
//            return sensorData;
        } catch (Exception e) {
            e.printStackTrace();
//            return null;
        }
    }

    public SensorData getData(){
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getUri("sensor-data")))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper objectMapper = new ObjectMapper();
            SensorData sensorData = objectMapper.readValue(response.body(), SensorData.class);
            return sensorData;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}

class InputOutput{
    Terminal terminal;
    public InputOutput(){
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
        } catch (Exception e) {

        }
    }

    public static Boolean processWall(Board board, Cell cell, Function<Cell, Boolean> wallChecker,
                                      Function<Cell, Boolean> wallCheckerNeighbour,
                                      int ny, int nx) {

        if (ny < 0 || ny >= board.matrix.size() || nx < 0 || nx >= board.matrix.getFirst().size()) {
            return wallChecker.apply(cell) == null || Boolean.TRUE.equals(wallChecker.apply(cell));
        }


        Cell neighbourCell = board.matrix.get(ny).get(nx);

        Boolean neighbourWall = wallCheckerNeighbour.apply(neighbourCell);
        Boolean currentWall = wallChecker.apply(cell);

        return (neighbourWall == null || Boolean.TRUE.equals(neighbourWall)) ||
                (currentWall == null || Boolean.TRUE.equals(currentWall));    }

    public void showBoard(Board board, SensorData sensorData, SensorData prev, Robot robot){
        terminal.writer().println("\033[H\033[2J");
        terminal.writer().println(
                sensorData.front_distance() + " " +
                        sensorData.right_side_distance() + " " +
                        sensorData.back_distance() + " " +
                        sensorData.left_side_distance()
        );

        terminal.writer().println(
                sensorData.down_y_offset() + " " +
                        sensorData.down_x_offset() + " " +
                        (sensorData.down_y_offset()-prev.down_y_offset()) + " " +
                        (sensorData.down_x_offset()-prev.down_x_offset())
        );
        terminal.writer().println(robot.diffX + " " + robot.diffY + " " + robot.isCentered);
        terminal.flush();
        for(int i = 0;i<16;i++){
            StringBuilder top = new StringBuilder();
            StringBuilder middle = new StringBuilder();
            StringBuilder bottom = new StringBuilder();
            for(int j = 0;j<16;j++){
                Cell cell = board.matrix.get(i).get(j);
                top.append("○");
                top.append(processWall(board, cell, c -> c.topWall, c -> c.bottomWall, i-1, j) ? "───" : "   ");

                middle.append(processWall(board, cell, c -> c.leftWall, c -> c.rightWall, i, j-1) ? "│" : " ");
                middle.append("   ");

                if(i == 15){
                    bottom.append("○");
                    bottom.append(processWall(board, cell, c -> c.bottomWall, c-> c.topWall, i+1, j) ? "───" : "   ");
                }

                if(j == 15){
                    top.append("○");
                    bottom.append("○");
                    middle.append(processWall(board, cell, c -> c.rightWall, c -> c.leftWall, i, j+1) ? "│" : " ");
                }
            }
            terminal.writer().println(top);
            terminal.writer().println(middle);
            if(i == 15){
                terminal.writer().println(bottom);
            }
            terminal.flush();
        }
    }
}

record SensorData(double front_distance, double right_side_distance, double left_side_distance, double back_distance,
                  double left_45_distance, double right_45_distance, double rotation_pitch, double rotation_yaw, double rotation_roll,
                  double down_x_offset, double down_y_offset){};

@FunctionalInterface
interface MoveAction{
    void execute();
}
