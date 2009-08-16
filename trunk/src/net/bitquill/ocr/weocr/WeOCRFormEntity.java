package net.bitquill.ocr.weocr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.entity.AbstractHttpEntity;

import android.graphics.Bitmap;

/**
 * Simple implementation of form/multipart entity with hard-coded fields, 
 * to avoid including mime4j and httpmime (~400K in two JARs).
 * 
 * @author spapadim
 */
public class WeOCRFormEntity extends AbstractHttpEntity {
    
    private static final String BOUNDARY = "----------------GfHio#@q%f1a&dDg#eQ@";  // monkey-typed random string
    private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;
    
    private static final String BODY_HEADER = 
        BOUNDARY + "\n" +
        "Content-Disposition: form-data; name=\"userfile\"; filename=\"text.png\"\n" +
        "Content-Type: image/png\n" +
        "Content-Transfer-Encoding: binary\n" +
        "\n";
    private static final String BODY_TRAILER = 
        BOUNDARY + "\n" +
        "Content-Disposition: form-data; name =\"outputformat\"\n" +
        "\n" +
        "txt\n" +
        BOUNDARY + "\n" +
        "Content-Disposition: form-data; name=\"outputencoding\"\n" +
        "\n" + 
        "utf-8\n" +
        BOUNDARY + "--";
    
    private Bitmap mImg;
    
    public WeOCRFormEntity (Bitmap img) {
        mImg = img;
        setContentType(CONTENT_TYPE);
        setChunked(false);
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getContentLength() {
        return -1;  // Unknown, because PNG compression is done on-the-fly
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public boolean isStreaming() {
        // TODO Auto-generated method stub
        return false;  // FIXME
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        if (os == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        os.write(BODY_HEADER.getBytes("ascii"));  // XXX check
        mImg.compress(Bitmap.CompressFormat.PNG, 80, os);
        os.write(BODY_TRAILER.getBytes("ascii")); // XXX check
        os.flush();
    }

}
