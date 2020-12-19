import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class GCodeGenerator {

    private static final String NEWLINE = "\n";
    public static final Locale LOCALE = Locale.ENGLISH;

    public static void main(String[] args) throws IOException {


        var pocketSizes = new ArrayList<Size>();
        for (int width = 100; width <= 1000; width += 50) {
            pocketSizes.add(new Size(width, 120));
            pocketSizes.add(new Size(width, 150));
            pocketSizes.add(new Size(width, 200));
        }

        var planSizes = new ArrayList<Size>();
        for (int width = 100; width <= 1000; width += 50) {
            for (int height = 100; height <= 2500;  height += 100) {
                planSizes.add(new Size(width, height));
            }
        }


        var setups = Arrays.asList(
                new Setup("pocket", 5000,  pocketSizes, 10, 0.9f, 5, 5),
                new Setup("pocket", 5000, pocketSizes, 10, 0.9f, 10, 5),
                new Setup("pocket", 5000, pocketSizes, 10, 0.9f, 15, 5),
                new Setup("pocket", 5000, pocketSizes, 10, 0.9f, 20, 5),
                new Setup("pocket", 5000, pocketSizes, 10, 0.9f, 25, 5),

                new Setup("pocket", 5000, pocketSizes, 10, 0.9f, 10, 10),
                new Setup("pocket", 5000, pocketSizes, 10, 0.9f, 20, 10),
                new Setup("pocket", 5000, pocketSizes, 10, 0.9f, 30, 10),

                new Setup("plane", 2000, planSizes, 60, 0.9f, 1, 1),
                new Setup("plane", 2000, planSizes, 60, 0.8f, 2, 2),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 5, 5),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 10, 3),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 10, 5),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 15, 3),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 15, 5),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 15, 8),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 20, 5),
                new Setup("plane", 2000, planSizes, 60, 0.5f, 20, 8)
        );

        var directory = new File("output");
        directory.mkdir();

        for (Setup setup : setups) {
            for (Size size : setup.sizes) {
                for (Direction direction : Direction.values()) {
                    var fileName = createFileName(setup, size, direction);

                    var path = new File(directory.getPath() + "/" + fileName);
                    new File(path.getParent()).mkdirs();

                    try (var file = new FileWriter(path, StandardCharsets.UTF_8)) {
                        file.write(gcode(setup, size, direction));
                    }
                }

            }

        }

    }

    enum Direction {
        HORIZONTAL("x"),
        VERTICAL("y");

        private final String axis;

        Direction(String axis) {

            this.axis = axis;
        }
    }

    private static String gcode(Setup setup, Size size, Direction direction) {

        int feedRate = 1000;

        var code = new GCodeWriter();
        var halfDiameter = (float) setup.diameter / 2;

        var gotoRetractHeight = "G0 Z20";
        var gotoStartPoint = String.format(LOCALE, "G0 X%.3f Y%.3f", halfDiameter, halfDiameter);

        code.append("(WIDTH=%d HEIGHT=%d)".formatted(size.width, size.height));
        code.append("(DIAMETER=%d)".formatted(setup.diameter));
        code.append("(DEPTH=%d MAX_DEPTH=%d)".formatted(setup.depth, setup.maxDepth));
        code.append("(SPINDLE=%d)".formatted(setup.spindleSpeed));

        // G91 - incremental distance mode In incremental distance mode, axis numbers usually represent increments from the current coordinate.
        // G94 - is Units per Minute Mode. In units per minute feed mode, an F word is interpreted to mean the controlled point should move at a certain number of inches per minute, millimeters per minute, or degrees per minute, depending upon what length units are being used and which axis or axes are moving.
        // G40 - turn cutter compensation off. If tool compensation was on the next move must be a linear move and longer than the tool diameter. It is OK to turn compensation off when it is already off.
        // G49 - cancels tool length compensation
        // G17 - XY (default)
        // G21 - to use millimeters for length units.
        code.append("G90 G94 G40 G49 G17 G21");

        // M5 - stop the spindle.
        code.append("M5");


        // M3 - start the spindle clockwise at the S speed.
        code.append("S%d M3".formatted(setup.spindleSpeed));

        // G54 - select coordinate system 1
        code.append("G54");

        code.append(gotoRetractHeight);

        code.comment("Visiting bounding box");

        // go to all 4 points
        code.append(gotoStartPoint);
        code.append(String.format(LOCALE, "G0 X%.3f", halfDiameter));
        code.append(String.format(LOCALE, "G0 X%.3f", size.width - halfDiameter));
        code.append(String.format(LOCALE, "G0 Y%.3f", size.height - halfDiameter));
        code.append(String.format(LOCALE, "G0 X%.3f", halfDiameter));

        code.append(gotoStartPoint);

        // go down slowly
        code.append("G1 Z0 F500");

        var z = 0;

        while (z > -setup.depth) {

            var safeZ = z + 10;

            z -= setup.maxDepth;
            z = Math.max(z, -setup.depth);

            code.comment("Z=%d".formatted(z));

            code.append(String.format(LOCALE, "G1 Z%d F100", z));
            var i = 0;


            code.append("F%d".formatted(feedRate));


            if (direction == Direction.VERTICAL) {
                for (var x = halfDiameter; x < size.width; x += (setup.diameter * setup.overlap)) {
                    var min = Math.min(x, size.width - halfDiameter);

                    var a = String.format(LOCALE, "X%.3f Y%.3f", min, halfDiameter);
                    var b = String.format(LOCALE, "X%.3f Y%.3f", min, size.height - halfDiameter);

                    if (i++ % 2 == 0) {
                        code.append(a);
                        code.append(b);
                    } else {
                        code.append(b);
                        code.append(a);
                    }
                }
            } else {
                for (var y = halfDiameter; y < size.height; y += (setup.diameter * setup.overlap)) {
                    var min = Math.min(y, size.height - halfDiameter);

                    var a = String.format(LOCALE, "X%.3f Y%.3f", halfDiameter, min);
                    var b = String.format(LOCALE, "X%.3f Y%.3f", size.width - halfDiameter, min);

                    if (i++ % 2 == 0) {
                        code.append(a);
                        code.append(b);
                    } else {
                        code.append(b);
                        code.append(a);
                    }
                }
            }


            code.append("GO Z%d".formatted(safeZ));
            code.append(gotoStartPoint);

        }

        // M2 - end the program.Pressing Cycle Start("R"in the Axis GUI) will restart the program at the beginning of the file.
        code.append("M2");

        return code.toString();
    }

    private static String createFileName(Setup setup, Size size, Direction direction) {
        List<String> paths = Arrays.asList(
                setup.prefix,
                String.format("%d", setup.diameter),
                String.format("%dx%d", size.width, size.height),
                String.format("%da%d-o%.0f-%s", setup.depth, setup.maxDepth, setup.overlap * 100, direction.axis)
        );

        return String.join("/", paths);
    }

    private static class GCodeWriter {
        private final StringBuilder stringBuilder = new StringBuilder();
        
        public GCodeWriter append(String line) {
            stringBuilder.append(line).append(NEWLINE);
            return this;
        }

        @Override
        public String toString() {
            return stringBuilder.toString();
        }

        public GCodeWriter comment(String comment) {
            append("").append("(" + comment + ")").append("");
            return this;
        }
    }
    
    private static final class Setup {

        private final String prefix;
        private final int spindleSpeed;
        private final List<Size> sizes;
        private final int diameter;
        private final float overlap;
        private final int depth;
        private final int maxDepth;

        private Setup(String prefix, int spindleSpeed, List<Size> sizes, int diameter, float overlap, int depth, int maxDepth) {

            this.prefix = prefix;
            this.spindleSpeed = spindleSpeed;
            this.sizes = sizes;
            this.diameter = diameter;
            this.overlap = overlap;
            this.depth = depth;
            this.maxDepth = maxDepth;
        }
    }

    private static final class Size {
        private final int width;
        private final int height;

        private Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

}
