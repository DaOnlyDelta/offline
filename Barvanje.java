import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Barvanje {

    private static class GridCase {
        final int n;
        final int d;
        final int b;
        final int c;
        final List<List<Character>> grid;

        GridCase(int n, int d, int b, int c, List<List<Character>> grid) {
            this.n = n;
            this.d = d;
            this.b = b;
            this.c = c;
            this.grid = grid;
        }

        public String getNumbers() {
            return String.format("n:%d d:%d b:%d c:%d\n", this.n, this.d, this.b, this.c);
        }
    }

    public static void main(String[] args) {
        List<GridCase> grids = getGrids("Barvanje.txt");
        for (GridCase gridCase : grids) {
            displayGrid(gridCase);
            firstTry(gridCase);
        }
    }

    private static List<GridCase> getGrids(String path) {
        List<GridCase> grids = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            System.out.println(br.readLine()); // optional title
            int gridCount = Integer.parseInt(br.readLine());

            String line;
            for (int g = 0; g < gridCount; g++) {
                while ((line = br.readLine()) != null && line.trim().isEmpty()) {
                    // skip seperators
                }
                line = br.readLine(); // read again to skip the grid indexes
                if (line == null) break;

                String[] parts = line.trim().split("\\s+");
                int n = Integer.parseInt(parts[0]);
                int d = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                int c = Integer.parseInt(parts[3]);

                List<List<Character>> grid = new ArrayList<>();
                for (int r = 0; r < n; r++) {
                    String rowLine = br.readLine();
                    List<Character> row = new ArrayList<>(rowLine.length());
                    for (char ch : rowLine.toCharArray()) {
                        row.add(ch);
                    }
                    grid.add(row);
                }
                grids.add(new GridCase(n, d, b, c, grid));
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return grids;
    }

    private static void firstTry(GridCase gridCase) {
        List<List<Character>> grid = gridCase.grid;
        for (int i = 0; i < grid.size(); i++) {
            List<Character> row = grid.get(i);
            for (int j = 0; j < row.size(); j++) {
                int wCounter = 0;
                int bCounter = 0;
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        if (di == 0 && dj == 0) {
                            continue;
                        }
                        int ni = i + di;
                        int nj = j + dj;
                        if (ni < 0 || nj < 0 || ni >= grid.size()) {
                            continue;
                        }
                        List<Character> nRow = grid.get(ni);
                        if (nj >= nRow.size()) {
                            continue;
                        }
                        char neighbor = nRow.get(nj);
                        if (neighbor == '.') {
                            wCounter++;
                        } else if (neighbor == '#') {
                            bCounter++;
                        }
                    }
                }
                // TODO: apply constraints using gridCase.d, gridCase.b, gridCase.c with the counters
            }
        }
    }

    public static void displayGrid(GridCase grids) {
        System.out.printf(grids.getNumbers());
        for (List<Character> row : grids.grid) {
            for (char cell : row) {
                switch (cell) {
                    case '.': System.out.print("⬜ "); break;
                    case '#': System.out.print("⬛ "); break;
                    case 'R': System.out.print("🟥 "); break;
                    default:  System.out.print(cell + " ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }
}