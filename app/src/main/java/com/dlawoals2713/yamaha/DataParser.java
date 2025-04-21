package com.yamaha.smafsynth.m7.emu;

import java.io.UnsupportedEncodingException;

/* renamed from: com.yamaha.smafsynth.m7.emu.a */
/* loaded from: classes.dex */
public class DataParser {

    /* renamed from: a */
    private byte[] data;

    /* renamed from: b */
    protected String encoding = "SJIS";

    /* renamed from: c */
    protected String title = "";

    /* renamed from: d */
    protected String artistName = "";

    /* renamed from: e */
    protected String copyrightInfo = "";

    /* renamed from: f */
    protected String genre = "";

    /* renamed from: g */
    protected String miscInfo = "";

    public DataParser(byte[] data) {
        this.data = null;
        this.data = data;
        parseData(this.data);
    }

    /* renamed from: a */
    private String getEncodingFromCode(int code) {
        if (code == 0) {
            return "SJIS";
        }
        if (code == 1) {
            return "ISO8859_1";
        }
        if (code == 2) {
            return "EUC_KR";
        }
        if (code == 3) {
            return "EUC_CN";
        }
        if (code == 4) {
            return "Big5";
        }
        if (code == 5) {
            return "KOI8_R";
        }
        switch (code) {
        }
        return "SJIS";
    }

    /* renamed from: a */
    private String parseString(byte[] byteArray, String encoding) {
        try {
            byteArray[0] = 0;
            byteArray[1] = 0;
            byteArray[2] = 0;
            return new String(byteArray, encoding).trim().replace("\\\\", "\\").replace("\\,", ",");
        } catch (UnsupportedEncodingException unused) {
            return "";
        }
    }

    /* renamed from: a */
    private void parseData(byte[] data) {
        int i;
        if (data == null) {
            return;
        }
        int headerLength = (data[12] & 255) + (data[13] & 255) + (data[14] & 255) + (data[15] & 255) + 15;
        int currentIndex = 0;
        if ((data[headerLength + 1] & 255) != 79 || (data[headerLength + 2] & 255) != 80 || (data[headerLength + 3] & 255) != 68 || (data[headerLength + 4] & 255) != 65) {
            int stringDataLength = (data[12] & 255) + (data[13] & 255) + (data[14] & 255) + (data[15] & 255);
            byte[] stringDataBytes = new byte[stringDataLength];
            this.encoding = getEncodingFromCode(data[18] & 255);
            System.arraycopy(data, 21, stringDataBytes, 0, stringDataLength);
            byte[] currentString = new byte[stringDataBytes.length];
            int currentStringIndex = 0;
            for (int i6 = 0; i6 < stringDataBytes.length; i6++) {
                if (i6 <= 1 || (((stringDataBytes[i6 - 2] & 255) == 92 && (stringDataBytes[i6 - 1] & 255) == 92 && (stringDataBytes[i6] & 255) == 44) || (((stringDataBytes[i6 - 1] & 255) == 92 && (stringDataBytes[i6] & 255) == 44) || (stringDataBytes[i6] & 255) != 44))) {
                    currentString[currentStringIndex] = stringDataBytes[i6];
                    currentStringIndex++;
                } else {
                    if ((currentString[0] & 255) == 83 && (currentString[1] & 255) == 84 && (currentString[2] & 255) == 58) {
                        this.title = parseString(currentString, this.encoding);
                    } else if ((currentString[0] & 255) == 65 && (currentString[1] & 255) == 78 && (currentString[2] & 255) == 58) {
                        this.artistName = parseString(currentString, this.encoding);
                    } else if ((currentString[0] & 255) == 67 && (currentString[1] & 255) == 82 && (currentString[2] & 255) == 58) {
                        this.copyrightInfo = parseString(currentString, this.encoding);
                    } else if ((currentString[0] & 255) == 71 && (currentString[1] & 255) == 82 && (currentString[2] & 255) == 58) {
                        this.genre = parseString(currentString, this.encoding);
                    } else {
                        if ((currentString[0] & 255) == 77 && (currentString[1] & 255) == 73 && (currentString[2] & 255) == 58) {
                            this.miscInfo = parseString(currentString, this.encoding);
                        }
                        currentString = new byte[stringDataBytes.length];
                        currentStringIndex = 0;
                    }
                    currentString = new byte[stringDataBytes.length];
                    currentStringIndex = 0;
                }
            }
            return;
        }
        int chunkHeaderStart = headerLength + 8;
        int chunkEnd = (data[headerLength + 5] & 255) + (data[headerLength + 6] & 255) + (data[headerLength + 7] & 255) + (data[chunkHeaderStart] & 255) + chunkHeaderStart;
        while (chunkHeaderStart < chunkEnd) {
            this.encoding = getEncodingFromCode(data[chunkHeaderStart + 4] & 255);
            int dataChunkStart = chunkHeaderStart + 8;
            int dataChunkEnd = (data[chunkHeaderStart + 5] & 255) + (data[chunkHeaderStart + 6] & 255) + (data[chunkHeaderStart + 7] & 255) + (data[dataChunkStart] & 255) + dataChunkStart;
            int currentChunkIndex = chunkHeaderStart;
            while (dataChunkStart < dataChunkEnd) {
                int identifierIndex = dataChunkStart + 1;
                if (((data[identifierIndex] & 255) == 83 && (data[dataChunkStart + 2] & 255) == 84) || (((data[identifierIndex] & 255) == 65 && (data[dataChunkStart + 2] & 255) == 78) || (((data[identifierIndex] & 255) == 67 && (data[dataChunkStart + 2] & 255) == 82) || (((data[identifierIndex] & 255) == 71 && (data[dataChunkStart + 2] & 255) == 82) || ((data[identifierIndex] & 255) == 77 && (data[dataChunkStart + 2] & 255) == 73))))) {
                    int stringLength = (data[dataChunkStart + 3] & 255) + (data[dataChunkStart + 4] & 255);
                    byte[] stringBytes = new byte[stringLength];
                    System.arraycopy(data, dataChunkStart + 5, stringBytes, currentIndex, stringLength);
                    try {
                        String parsedString = new String(stringBytes, this.encoding);
                        if ((data[identifierIndex] & 255) == 83 && (data[dataChunkStart + 2] & 255) == 84) {
                            this.title = parsedString;
                        } else if ((data[identifierIndex] & 255) == 65 && (data[dataChunkStart + 2] & 255) == 78) {
                            this.artistName = parsedString;
                        } else if ((data[identifierIndex] & 255) == 67 && (data[dataChunkStart + 2] & 255) == 82) {
                            this.copyrightInfo = parsedString;
                        } else if ((data[identifierIndex] & 255) == 71 && (data[dataChunkStart + 2] & 255) == 82) {
                            this.genre = parsedString;
                        } else if ((data[identifierIndex] & 255) == 77 && (data[dataChunkStart + 2] & 255) == 73) {
                            this.miscInfo = parsedString;
                        }
                    } catch (UnsupportedEncodingException e2) {
                        e2.printStackTrace();
                    }
                    i = dataChunkStart + stringLength + 3;
                } else {
                    i = dataChunkStart + (data[dataChunkStart + 3] & 255) + (data[dataChunkStart + 4] & 255) + 3;
                }
                currentChunkIndex += i;
                dataChunkStart = i + 1;
                currentIndex = 0;
            }
            chunkHeaderStart = currentChunkIndex + 1;
            currentIndex = 0;
        }
    }

    /* renamed from: a */
    public String getGenre() {
        return this.genre;
    }

    /* renamed from: b */
    public String getMiscInfo() {
        return this.miscInfo;
    }

    /* renamed from: c */
    public String getArtistName() {
        return this.artistName;
    }

    /* renamed from: d */
    public String getCopyrightInfo() {
        return this.copyrightInfo;
    }

    /* renamed from: e */
    public String getTitle() {
        return this.title;
    }
}