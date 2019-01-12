package jks.tools2d.packing.dragAndDrop;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import com.badlogic.gdx.Gdx;

public class DragDrop extends JFrame 
{
    private DropTarget dt;
    private final JTextArea txtContent = new JTextArea(30, 100);
 
    
    public DragDrop() 
    {
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(txtContent);

        Gdx.app.getApplicationListener() ;
        new DropTarget() ; 
        dt = new DropTarget(txtContent, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                dropIntoTextArea(dtde);
            }
        });
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
 
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
 
    public void dropIntoTextArea(DropTargetDropEvent dtde) {
        try {
            // Ok, get the dropped object and try to figure out what it is
            Transferable tr = dtde.getTransferable();
            DataFlavor[] flavors = tr.getTransferDataFlavors();
            for (int i = 0; i < flavors.length; i++) {
                //  System.out.println("Possible flavor: "
                //        + flavors[i].getMimeType());
                // Check for file lists specifically
                if (flavors[i].isFlavorJavaFileListType()) {
                    // Great! Accept copy drops...
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
 
 
                    // And add the list of file names to our text area
                    java.util.List list = (java.util.List) tr
                            .getTransferData(flavors[i]);
                    for (int j = 0; j < list.size(); j++) {
                        txtContent.append(getContent(list.get(j).toString()) + "\n");
                    }
 
                    // If we made it this far, everything worked.
                    dtde.dropComplete(true);
                    return;
                }
                // Ok, is it another Java object?
                else if (flavors[i].isFlavorSerializedObjectType()) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
 
                    Object o = tr.getTransferData(flavors[i]);
                    txtContent.append(String.valueOf(o));
                    dtde.dropComplete(true);
                    return;
                }
                // How about an input stream?
                else if (flavors[i].isRepresentationClassInputStream()) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
 
                    txtContent.read(new InputStreamReader((InputStream) tr
                                    .getTransferData(flavors[i])),
                            "from system clipboard"
                    );
                    dtde.dropComplete(true);
                    return;
                }
                // How about plain text?
                else if (flavors[i].isFlavorTextType()) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    InputStreamReader stream=(InputStreamReader)tr.getTransferData(flavors[i]);
                    BufferedReader in = new BufferedReader(stream);
                    String line = null;
                    while((line = in.readLine()) != null) {
                        txtContent.append(line+"\n");
                    }
                    dtde.dropComplete(true);
                    return;
                }
            }
            // Hmm, the user must not have dropped a file list
            System.out.println("Drop failed: " + dtde);
            dtde.rejectDrop();
        } catch (Exception e) {
            e.printStackTrace();
            dtde.rejectDrop();
        }
    }
    public String getContent(String path) throws IOException {
 
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"UTF-8"));
        String sCurrentLine, result = "";
        while ((sCurrentLine = br.readLine()) != null) {
            result += sCurrentLine + "\n";
        }
        return result;
    }
 
    public static void main(String[] args) 
    {
        new DragDrop();
    }
 
}