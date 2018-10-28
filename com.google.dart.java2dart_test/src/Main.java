package src;

import com.google.dart.engine.ast.CompilationUnit;
import com.google.dart.java2dart.Context;
import com.google.dart.java2dart.processor.RenameConstructorsSemanticProcessor;

import java.io.*;
import java.io.File;
import java.util.ArrayList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
public class Main {
    public static void main(String[] args) throws IOException {

//        SemanticTest trans = new SemanticTest();
//        trans.test_anonymousClass_extendsClass();
//        Test1 test = new Test1();
//        try {
//            test.test_Class();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        String path=System.getProperty("user.dir")+"/out/1";
        readFile(path);
//        readAllFile(path);
//        System.out.println(listname.size());
//        File file=new File(path);
//        File[] tempList = file.listFiles();
//        System.out.println("该目录下对象个数："+tempList.length);
//        for (int i = 0; i < tempList.length; i++) {
//            if (tempList[i].isFile()) {
//                System.out.println("文     件：" + tempList[i]);
//            }
//
//            if (tempList[i].isDirectory()) {
//                System.out.println("文件夹：" + tempList[i]);
//                File file1=new File(path+tempList[i]);
//                File[] tempList1 = file.listFiles();
//                for (int j = 0; j < tempList.length; j++) {
//
//                }
//            }
//        }
    }




//        private static ArrayList<String> listname = new ArrayList<String>();
//
//        public static void readAllFile(String filepath) {
//            File file= new File(filepath);
//            if(!file.isDirectory()){
//                listname.add(file.getName());
//            }else if(file.isDirectory()){
//                System.out.println("文件");
//                String[] filelist=file.list();
//                for(int i = 0;i<filelist.length;i++){
//                    File readfile = new File(filepath);
//                    if (!readfile.isDirectory()) {
//                        listname.add(readfile.getName());
//                    } else if (readfile.isDirectory()) {
//                        readAllFile(filepath + "\\" + filelist[i]);//递归
//                    }
//                }
//            }
//            for(int i = 0;i<listname.size();i++){
//                System.out.println(listname.get(i));
//            }
//        }



        /**
         * 递归读取某个目录下的所有文件
         *
         * @author
         * @Date
         * @motto 人在一起叫聚会，心在一起叫团队
         * @Version 1.0
         */
        private static void readFile(String fileDir) throws IOException {
            List<File> fileList = new ArrayList<File>();
            File file = new File(fileDir);
            File[] files = file.listFiles();// 获取目录下的所有文件或文件夹
            String[] ss = {""};
            if (files == null) {// 如果目录为空，直接退出
                return;
            }

//            Test1 test = null;
            // 遍历，目录下的所有文件
            for (File f : files) {
                if (f.isFile()) {
                    System.out.println("  => "+f.getName());


                    FileReader reader = new FileReader(f);//定义一个fileReader对象，用来初始化BufferedReader
                    BufferedReader bReader = new BufferedReader(reader);//new一个BufferedReader对象，将文件内容读取到缓存
                    StringBuilder sb = new StringBuilder();//定义一个字符串缓存，将字符串存放缓存中
                    String s = "";
                    while((s = bReader.readLine()) != null) {
                        //逐行读取文件内容，不读取换行符和末尾的空格
                        sb.append(s + "\n");//将读取的字符串添加换行符后累加存放在缓存中
                    }

                    bReader.close();
                    String str = sb.toString();
//                    System.out.println(str);

                    Test1 test = new Test1(ss[ss.length-1], f.getName(), str);
                    try {
                        test.test_Class();
//                        test.testSyntax(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    fileList.add(f);
                } else if (f.isDirectory()) {
//                    test = new Test1(f.getName(), "");
//                    test.tempfiledel();
                    ss = f.getAbsolutePath().split("\\/");

                    System.out.println();
                    System.out.println(ss[ss.length-1]+":");
                    System.out.println(f+":");
                    readFile(f.getAbsolutePath());

//                    String[] ss = f.getAbsolutePath().split("\\/");
//                    System.out.println("  ss "+ss[ss.length-1]);
                }
            }
//            for (File f1 : fileList) {
//                System.out.println("  => "+f1.getName());
//            }


        }



}

