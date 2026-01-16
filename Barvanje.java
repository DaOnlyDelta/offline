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
            checkGrid(grids.get(0));
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
                if (line == null)
                    break;

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
                // TODO: apply constraints using gridCase.d, gridCase.b, gridCase.c with the
                // counters
            }
        }
    }

    private static boolean checkGrid(GridCase grids) {
        int bCounter, wCounter;
        List<List<Character>> grid = grids.grid;
        for (int i = 0; i < grid.size(); i++) {
            for (int j = 0; j < grid.get(i).size(); j++) {
                bCounter = 0;
                wCounter = 0;
                int topOffset = i - grids.d; // Get the top offset depending on the column and d
                for (int k = (topOffset > 0) ? topOffset : 0; k <= i + grids.d && k < grids.n; k++) {
                    // Check every box in i - d lines above to i + d
                    // Check if i - d is < 0 and if i + d > n
                    int leftOffset = j - grids.d; // Get the left offset depending on the position in the row and d
                    List<Character> row = grid.get(k);
                    for (int l = (leftOffset > 0) ? leftOffset : 0; l <= j + grids.d && l < grids.n; l++) {
                        // Go trough every row from j - d to j + d, making sure to not check the box itself
                        // Check if j - d is < 0 and if j + d > n
                        if (l == j && k == i) continue; // Skip self check
                        Character c = row.get(l);
                        if (c == '.') {
                            wCounter++;
                        } else if (c == '#') {
                            bCounter++;
                        }
                    }
                }
                if (bCounter != 0) {
                    System.out.printf("[%d, %d] = %d ⬛\n", i, j, bCounter);
                }
            }
        }
        return true;
    }

    private static void displayGrid(GridCase grids) {
        System.out.printf(grids.getNumbers());
        for (List<Character> row : grids.grid) {
            for (char cell : row) {
                switch (cell) {
                    case '.':
                        System.out.print("⬜ ");
                        break;
                    case '#':
                        System.out.print("⬛ ");
                        break;
                    case 'R':
                        System.out.print("🟥 ");
                        break;
                    default:
                        System.out.print(cell + " ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }
}