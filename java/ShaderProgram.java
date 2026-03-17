import java.nio.file.Files;
import java.nio.file.Paths;
import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {

    private int programId;

    public ShaderProgram(String vertPath, String fragPath) {
        int v = compile(vertPath, GL_VERTEX_SHADER);
        int f = compile(fragPath, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, v);
        glAttachShader(programId, f);
        glLinkProgram(programId);

        glDeleteShader(v);
        glDeleteShader(f);
    }

    private int compile(String path, int type) {
        try {
            String src = new String(Files.readAllBytes(Paths.get(path)));
            int id = glCreateShader(type);
            glShaderSource(id, src);
            glCompileShader(id);
            return id;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void bind() { glUseProgram(programId); }
    public void unbind() { glUseProgram(0); }
}
