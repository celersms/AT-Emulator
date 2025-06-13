/* Copyright CelerSMS, 2018-2025
 * https://www.celersms.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Author: Victor Celer
 */
package com.celer.emul;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

// AT Emulator
public final class AT extends Thread{

   static List<W> ths;
   static int port, ww, maxcon, conn;
   static boolean request;
   private static final String[] serr = {
      "OK",
      "+CMS ERROR: 304", // one or more parameter values assigned to the AT command are invalid (PDU mode)
      "+CMS ERROR: 305", // one or more parameter values assigned to the AT command are invalid (text mode)
      "NO CARRIER",
      "ERROR"
   };
   private static final byte srdef[] = {
      /* S0  */ 0,  // number of rings before auto-answer
      /* S1  */ 0,  // ring counter
      /* S2  */ 43, // escape character ('+')
      /* S3  */ 13, // carriage return character ('\r')
      /* S4  */ 10, // line feed character ('\n')
      /* S5  */ 8,  // backspace character
      /* S6  */ 2,  // wait time before blind dialing
      /* S7  */ 50, // wait for carrier after dial
      /* S8  */ 2,  // pause time for comma (dial delay)
      /* S9  */ 6,  // carrier detect response time (0.6s)
      /* S10 */ 14, // delay between loss of carrier and hang-up (1.4s)
      /* S11 */ 95, // DTMF tone duration in ms
      /* S12 */ 50, // escape code guard time in 1/50s (1s)
                0, 0, 0, 0, 0, // N/A
      /* S18 */ 0,  // test timer
                0, 0, 0, 0, 0, 0, // N/A
      /* S25 */ 5,  // delay to DTR
      /* S26 */ 1,  // RTS to CTS delay interval in 1/100s
                0, 0, 0,
      /* S30 */ 0,  // inactivity disconnect timer
                0, 0, 0, 0, 0, 0,
      /* S37 */ 0,  // desired telco line speed
      /* S38 */ 20  // delay before force disconnect
   };

   private AT(){ /* NOOP */ }

   public static final void main(String[] args){
      port = ww = maxcon = 0;
      if(args.length > 0)
         port = s2i(args[0]);
      if(port > 0){
         if(args.length > 1)
            ww = s2i(args[1]);
         if(args.length > 2)
            maxcon = s2i(args[2]);
         if(ww < 1 || ww > 99)
            ww = 4;
         if(maxcon > 0 && ww > maxcon)
            ww = maxcon;
         new AT().start();
      }
      doLoop(new InputStreamReader(System.in), System.out, true);
   }

   // AT processing loop
   static final void doLoop(InputStreamReader iin, PrintStream out, boolean banner){
      boolean doST, verb = true, quiet = false, echo = true;
      BufferedReader in = null;
      StringBuilder sb = new StringBuilder(78);
      String line, rs;
      long cmd;
      byte sr[] = new byte[39];
      System.arraycopy(srdef, 0, sr, 0, 39);
      int ii, jj, kk, ll, mm, cc, err, p1, p2, p3, p4, p5, prevcc, cursr = 0, mr = 0, atx = 4;
      byte cnmi_mode = 1, cnmi_mt = 2, cnmi_bm = 0, cnmi_ds = 1, cnmi_bfr = 0, creg_nn = 0, att_mode = 0, sms_mode = 1, mode = 1; // text mode
      try{
         in = new BufferedReader(iin);
         if(banner && !in.ready()){
            sb.append("AT Emulator");
            if(port > 0){
               sb.append(" on port ").append(port).append(',').append(' ').append(ww).append(" workers");
               if(maxcon > 0)
                  sb.append(',').append(' ').append(maxcon).append(" max. connections");
            }
            out.print(sb.append("\r\nCtrl+C to exit\r\n").toString());
         }
AT_LOOP: while((line = in.readLine()) != null){

            // Each line can contain a single AT command or multiple concatenated AT commands
            ii = kk = 0;
            ll = line.length();
            doST = false;
            do{

               // Get next AT command
               cmd = p1 = p2 = p3 = p4 = p5 = prevcc = -1;
               while(ii < ll && (cc = (byte)line.charAt(ii++)) != 0x3B){

                  // Telnet
                  if(!banner && cc < 0 && kk == 0)
                     while(ii < ll && (cc = (byte)line.charAt(ii++)) < 0x41);

                  if(cc > 0x20){
                     cc |= 0x20; // to lowercase
                     if(++kk > 2 && cmd == -1)
                        cmd = 0;
                     if(kk == 1){
                        if(cc == 0x61) // 'A'
                           continue;
                        if(cc == 0x2B){ // '+'
                           cmd = cc;
                           continue;
                        }
                        ii = ll;
                        break;
                     }
                     if(kk == 2){
                        if(cc == 0x74){ // 'T'
                           cmd = 0;
                           continue;
                        }
                        if(cc == 0x2B){ // '+'
                           cmd = cmd << 8 | cc;
                           continue;
                        }
                        ii = ll;
                        break;
                     }
                     if((cc == 0x30 && prevcc >= 0x61 && prevcc <= 0x7A) || // trailing '0'
                        (cmd == 0x64 && ((cc >= 0x28 && cc <= 0x39) || cc == 0x60 || cc == 0x23))) // ignore dial number for ATD
                        continue;
                     if((cmd = cmd << 8 | (prevcc = cc)) == 0x73){ // ATS[n]
                        cc = 0x3B;
                        while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x3D && cc != 0x3F && cc != 0x3B){
                           if(cc >= 0x30 && cc <= 0x39){
                              if(p1 == -1)
                                 p1 = 0;
                              p1 = p1 * 10 + cc - 0x30;
                           }else if(cc > 0x20)
                              cmd = cmd << 8 | cc | 0x20;
                           cc = 0x3B;
                        }
                        if(cc != 0x3B)
                           cmd = 0x7320 | cc;
                        if(cc == 0x3D){ // ATS[n]=
                           while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x3B)
                              if(cc >= 0x30 && cc <= 0x39){
                                 if(p2 == -1)
                                    p2 = 0;
                                 p2 = p2 * 10 + cc - 0x30;
                              }else if(cc > 0x20)
                                 cmd = cmd << 8 | cc | 0x20;
                        }
                        if(cc == 0x3B)
                           break;
                     }else if(cmd == 0x00002B6370696E3DL){ // AT+CPIN=
                        while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x3B){
                           if(cc >= 0x30 && cc <= 0x39){
                              if(p1 == -1)
                                 p1 = 0;
                              p1 = p1 * 10 + cc - 0x30;
                           }else if(cc > 0x20)
                              cmd = cmd << 8 | cc | 0x20;
                        }
                     }else if(cmd == 0x00002B636E6D693DL){ // AT+CNMI=
                        cc = 0;

                        // <mode>
                        while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x2C && cc != 0x3B){
                           if(cc >= 0x30 && cc <= 0x39){
                              if(p1 == -1)
                                 p1 = 0;
                              p1 = p1 * 10 + cc - 0x30;
                           }else if(cc > 0x20)
                              cmd = cmd << 8 | cc | 0x20;
                        }
                        if(cc == 0x2C){
                           cc = 0;

                           // <mt>
                           while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x2C && cc != 0x3B){
                              if(cc >= 0x30 && cc <= 0x39){
                                 if(p2 == -1)
                                    p2 = 0;
                                 p2 = p2 * 10 + cc - 0x30;
                              }else if(cc > 0x20)
                                 cmd = cmd << 8 | cc | 0x20;
                           }
                           if(cc == 0x2C){
                              cc = 0;

                              // <bm>
                              while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x2C && cc != 0x3B){
                                 if(cc >= 0x30 && cc <= 0x39){
                                    if(p3 == -1)
                                       p3 = 0;
                                    p3 = p3 * 10 + cc - 0x30;
                                 }else if(cc > 0x20)
                                    cmd = cmd << 8 | cc | 0x20;
                              }
                              if(cc == 0x2C){
                                 cc = 0;

                                 // <ds>
                                 while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x2C && cc != 0x3B){
                                    if(cc >= 0x30 && cc <= 0x39){
                                       if(p4 == -1)
                                          p4 = 0;
                                       p4 = p4 * 10 + cc - 0x30;
                                    }else if(cc > 0x20)
                                       cmd = cmd << 8 | cc | 0x20;
                                 }
                                 if(cc == 0x2C){
                                    cc = 0;

                                    // <bfr>
                                    while(ii < ll && (cc = line.charAt(ii++) & 0x7F) != 0x2C && cc != 0x3B){
                                       if(cc >= 0x30 && cc <= 0x39){
                                          if(p5 == -1)
                                             p5 = 0;
                                          p5 = p5 * 10 + cc - 0x30;
                                       }else if(cc > 0x20)
                                          cmd = cmd << 8 | cc | 0x20;
                                    }
                                 }
                              }
                           }
                        }
                     }else if(cmd == 0x00002B636D67733DL) // AT+CMGS=
                        while(ii < ll){
                           if((cc = line.charAt(ii++)) >= 0x30 && cc <= 0x39){
                              if(p1 == -1)
                                 p1 = 0;
                              p1 = p1 * 10 + cc - 0x30;
                           }else if(cc > 0x22 && cc != 0x2B)
                              cmd = cmd << 8 | cc | 0x20;
                        }
                  }
               }

               // Process next AT command
               rs = null;
               err = 0; // OK
               mm = (int)(cmd >>> 32);
               sb.setLength(0);
               switch(jj = (int)cmd){
               case 0x00000000: // AT
               case 0x00000061: // ATA (answers an incoming voice call)
               case 0x0000006C: // ATL[0] (turn off speaker)
               case 0x00006C31: // ATL1 (speaker volume = 1)
               case 0x00006C32: // ATL1 (speaker volume = 2)
               case 0x00006C33: // ATL1 (speaker volume = 3)
               case 0x0000006D: // ATM[0] (speaker off)
               case 0x00006D31: // ATM1 (speaker on until remote carrier detected)
               case 0x00006D32: // ATM2 (speaker always on)
               case 0x00006D33: // ATM3 (speaker off - proprietary)
               case 0x0000006F: // ATO (return online)
               case 0x00002663: // AT&C[0] (DCD always on)
               case 0x00266331: // AT&C1 (DCD follows the connection, default value)
               case 0x00002664: // AT&D[0] (ignore, default value)
               case 0x00266431: // AT&D1 (when in online data mode, switch to online command mode)
               case 0x00266432: // AT&D2 (disconnect and switch to offline command mode)
                  break;
               case 0x00000064: // ATD[XXXXXXX] (dial the number XXXXXXX in data mode)
               case 0x0000643B: // ATD[XXXXXXX]; (dial the number XXXXXXX in voice mode)
               case 0x0000646C: // ATDL (dial the last dialled number)
               case 0x00000068: // ATH[0] (hangs up the phone, end any call in progress)
               case 0x00006831: // ATH1 (pick up the phone line)
                  err = 3; // NO CARRIER
                  break;
               case 0x00000069: // ATI[0] (4-digit product code)
                  rs = "C102";
                  break;
               case 0x00006931: // ATI1 (ROM checksum)
               case 0x00006932: // ATI2 (ROM checksum + validate)
                  rs = "63656C6572736D73";
                  break;
               case 0x63676D69: // AT+CGMI (information about device manufacturer)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
               case 0x00006933: // ATI3 (manufacturer)
                  rs = "CelerSMS";
                  break;
               case 0x63676D6D: // AT+CGMM (information about the model of the device)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
               case 0x00006934: // ATI4 (product name)
                  rs = "CelerSMS AT Emulator";
                  break;
               case 0x63676D72: // AT+CGMR (revision information)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
               case 0x00006935: // ATI5 (version)
                  rs = "1.0.2";
                  break;
               case 0x6D693D3F:
                  switch(mm){
                  case 0x002B636E: // AT+CNMI=? (query supported values for AT+CNMI=)
                     rs = "+CNMI: (0-2),(0-3),(0,2),(0-2),(0,1)";
                     break;
                  case 0x002B6367: // AT+CGMI=?
                  case 0x002B6369: // AT+CIMI=? (IMSI availability)
                     break;
                  default:
                     err = 4;
                  }
                  break;
               case 0x6D6D3D3F: // AT+CGMM=?
               case 0x6D723D3F: // AT+CGMR=?
                  if(mm != 0x002B6367){
                     err = 4;
                     break;
                  }
                  break;
               case 0x67733D3F: // AT+CMGS=?
                  if(mm != 0x002B636D){
                     err = 4;
                     break;
                  }
                  break;
               case 0x6D67733D: // AT+CMGS= (send SMS)
                  if(mm != 0x00002B63 || (mode == 0 && (p1 < 7 || p1 > 160)) || (mode == 1 && p1 == -1)){
                     err = 4;
                     break;
                  }
                  out.print('>');
                  p3 = mode == 0 ? (p1 + 12) << 1 : 160;
                  p2 = 0;
                  while((cc = in.read()) != 26 && cc != -1) // Ctrl+Z
                     if(++p2 < p3)
                        sb.append((char)cc);
                  if(in.ready()) // a workaround for the extra <CR> in Windows
                     in.readLine();
                  if(mode == 0){ // PDU mode
                     if(p2 > p3){ // PDU oversized
                        err = 1;
                        break;
                     }
//                     mm = 0;
//                     while(mm < p2)
//                        if(((cc = sb.charAt(mm++) | 0x20) < 0x30 || cc > 0x39) && (cc < 0x61 || cc > 0x66))
//                           break;
//                     if((p2 & 1) != 0){ // not hex
//                        err = 1;
//                        break;
//                     }
                  }else if(p2 > 160){ // text mode
                     err = 2;
                     break;
                  }
                  sb.setLength(0);
                  if((mr = (mr + 1) & 0xFF) == 0)
                     mr = 1;
                  rs = sb.append("+CMGS: ").append(mr).toString();
                  break;
               case 0x00000078: // ATX[0] (Smartmodem result codes)
                  atx = 0;
                  break;
               case 0x00007831: // ATX1 (normal result codes)
               case 0x00007832: // ATX2 (normal result codes)
               case 0x00007833: // ATX3 (normal result codes)
               case 0x00007834: // ATX4 (normal result codes)
                  atx = (byte)(jj & 0x0F);
                  break;
               case 0x00000065: // ATE[0] (no echo)
                  echo = false;
                  break;
               case 0x00006531: // ATE1 (echo)
                  echo = true;
                  break;
               case 0x00000076: // ATV[0] (numeric error codes)
                  verb = false;
                  break;
               case 0x00007631: // ATV1 (verbose error codes)
                  verb = true;
                  break;
               case 0x00000071: // ATQ[0] (quiet mode off)
                  quiet = false;
                  break;
               case 0x00007131: // ATQ1 (quiet mode on)
                  quiet = true;
                  break;
               case 0x0000733F: // ATS[n]? (select and read register Sn)
                  if(p1 == -1)
                     p1 = cursr;
               case 0x00000073: // ATS[n] (select register Sn)
                  if(p1 == -1)
                     p1 = 0;
                  if(p1 < 0 || p1 > 38){
                     err = 4;
                     break;
                  }
                  cursr = p1;
                  if(jj != 0x00000073)
                     rs = Integer.toString((int)sr[p1] & 0xFF);
                  break;
               case 0x0000733D: // ATS[n]= (write register Sn)
                  if(p1 == -1)
                     p1 = cursr;
                  if(p1 < 0 || p1 > 38 || p2 < 0 || p2 > 255){
                     err = 4;
                     break;
                  }
                  sr[p1] = (byte)p2;
                  break;
               case 0x00733D3F: // ATS[n]=? (valid range for Sn)
                  if(p1 == -1)
                     p1 = cursr;
                  if(p2 != -1 || p1 < 0 || p1 > 38){
                     err = 4;
                     break;
                  }
                  rs = sb.append('S').append(p1).append(":(0..255)").toString();
                  break;
               case 0x0000007A: // ATZ[0] (reset, profile 0)
               case 0x00007A31: // ATZ1 (reset, profile 1)
                  echo = verb = true;
                  quiet = false;
                  atx = 4;
                  cnmi_mode = cnmi_ds = mode = 1;
                  cnmi_mt = 2;
                  cursr = cnmi_bm = cnmi_bfr = creg_nn = att_mode = 0;
                  System.arraycopy(srdef, 0, sr, 0, 39);
                  break;
               case 0x6D67663F: // AT+CMGF? (query the current text mode vs. PDU mode)
                  if(mm != 0x00002B63){
                     err = 4;
                     break;
                  }
                  rs = sb.append("+CMGF: ").append((char)(0x30 + mode)).toString();
                  break;
               case 0x67663D3F: // AT+CMGF=? (query supported modes: text mode or PDU mode)
                  if(mm != 0x002B636D){
                     err = 4;
                     break;
                  }
                  rs = "+CMGF: (0-1)";
                  break;
               case 0x67663D30: // AT+CMGF=0 (set PDU mode)
               case 0x67663D31: // AT+CMGF=1 (set text mode)
                  if(mm != 0x002B636D){
                     err = 4;
                     break;
                  }
                  mode = (byte)(jj & 0x0F);
                  break;
               case 0x2B637371: // AT+CSQ (get signal strength)
                  if(mm != 0x00000000){
                     err = 4;
                     break;
                  }
                  rs = "+CSQ: 21,0"; // RSSI 21 = -71 dBm, BER N/A
                  break;
               case 0x73713D3F: // AT+CSQ=? (test signal strength availability)
                  if(mm != 0x00002B63){
                     err = 4;
                     break;
                  }
                  rs = "+CSQ: (0-31,99),(0-7,99)";
                  break;
               case 0x69643D3F: // AT+CCID=? (ICCID availability)
                  if(mm != 0x002B6363){
                     err = 4;
                     break;
                  }
                  break;
               case 0x63636964: // AT+CCID (get ICCID)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
                  // 89        = MII: Telecom
                  // 999       = CCI: reserved
                  // 99        = MNC: Internal use
                  // 012345678 = MSIN
                  rs = "8999999012345678";
                  break;
               case 0x63696D69: // AT+CIMI (get IMSI)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
                  // 999       = MCC: Internal use
                  // 999       = MNC: Internal use
                  // 012345678 = MSIN
                  rs = "999999012345678";
                  break;
               case 0x736E3D3F: // AT+CGSN=? (IMEI availability)
                  if(mm != 0x002B6367){
                     err = 4;
                     break;
                  }
                  break;
               case 0x736E3D30: // AT+CGSN=0 (get serial number = IMEI)
                  mm = mm == 0x002B6367 ? 0x0000002B : 0;
               case 0x6367736E: // AT+CGSN (get IMEI)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
                  // 001 = Test ME
                  // 000 = GSM NA
                  // 00  = RFU
                  // 000 = Test ME type
                  // 123 = serial number
                  rs = "00100000000123";
                  break;
               case 0x74743D30: // AT+CGATT=0 (detach from GPRS)
               case 0x74743D31: // AT+CGATT=1 (attach to GPRS)
                  if(mm != 0x2B636761){
                     err = 4;
                     break;
                  }
                  att_mode = (byte)(jj & 0x0F);
                  break;
               case 0x6174743F: // AT+CGATT? (read current GPRS attach mode)
                  if(mm != 0x002B6367){
                     err = 4;
                     break;
                  }
                  rs = sb.append("+CGATT: ").append((char)(0x30 + att_mode)).toString();
                  break;
               case 0x74743D3F: // AT+CGATT=? (query GPRS attach availability)
                  if(mm != 0x2B636761){
                     err = 4;
                     break;
                  }
                  rs = "+CGATT: (0,1)";
                  break;
               case 0x6D733D30: // AT+CGSMS=0 (MO SMS mode: GPRS)
               case 0x6D733D31: // AT+CGSMS=1 (MO SMS mode: circuit switched)
               case 0x6D733D32: // AT+CGSMS=2 (MO SMS mode: GPRS preferred)
               case 0x6D733D33: // AT+CGSMS=3 (MO SMS mode: circuit switched preferred)
                  if(mm != 0x2B636773){
                     err = 4;
                     break;
                  }
                  sms_mode = (byte)(jj & 0x0F);
                  break;
               case 0x736D733F: // AT+CGSMS? (read select service for MO SMS mode)
                  if(mm != 0x002B6367){
                     err = 4;
                     break;
                  }
                  rs = sb.append("+CGSMS: ").append((char)(0x30 + sms_mode)).toString();
                  break;
               case 0x6D733D3F: // AT+CGSMS=? (query select service for MO SMS availability)
                  if(mm != 0x2B636773){
                     err = 4;
                     break;
                  }
                  rs = "+CGSMS: (0-3)";
                  break;
               case 0x70696E3D: // AT+CPIN=
                  if(mm != 0x00002B63 || p1 < 0 || p1 > 9999){
                     err = 4;
                     break;
                  }
                  break;
               case 0x70696E3F: // AT+CPIN?
                  if(mm != 0x00002B63){
                     err = 4;
                     break;
                  }
                  rs = "+CPIN: \"READY\"";
                  break;
               case 0x696E3D3F: // AT+CPIN=?
                  if(mm != 0x002B6370){
                     err = 4;
                     break;
                  }
                  break;
               case 0x00002676: // AT&V (status summary)
                  sb.append("ACTIVE PROFILE:\r\n").append(echo ? "E1" : "E0").append(quiet ? " Q1" : " Q0").append(verb ? " V1 X" : " V0 X"
                     ).append((char)(0x30 + atx)).append('\r').append('\n');
                  for(mm = 0; mm < 10; mm++)
                     sb.append('S').append('0').append((char)(0x30 + mm)).append(':').append((int)sr[mm] & 0xFF).append(' ');
                  rs = sb.append("\r\nS10:").append((int)sr[10] & 0xFF).append(" S11:").append((int)sr[11] & 0xFF).append(" S12:").append((int)sr[12] & 0xFF
                     ).append(" S18:").append((int)sr[18] & 0xFF).append(" S25:").append((int)sr[25] & 0xFF).append(" S26:").append((int)sr[26] & 0xFF
                     ).append(" S30:").append((int)sr[30] & 0xFF).append(" S37:").append((int)sr[37] & 0xFF).append(" S38:").append((int)sr[38] & 0xFF
                     ).append("\r\nSTORED PROFILE 0:\r\nE1 Q0 V1 X4\r\nS00:0 S01:0 S02:43 S03:13 S04:10 S05:8 S06:2 S07:50 S08:2 S09:6\r\nS10:14 S11:95 S12:50 S18:0 S25:5 S26:1 S30:0 S37:0 S38:20"
                     ).append("\r\nSTORED PROFILE 1:\r\nE1 Q0 V1 X4\r\nS00:0 S01:0 S02:43 S03:13 S04:10 S05:8 S06:2 S07:50 S08:2 S09:6\r\nS10:14 S11:95 S12:50 S18:0 S25:5 S26:1 S30:0 S37:0 S38:20"
                     ).toString();
                  break;
               case 0x6E6D693D: // AT+CNMI=
                  if(mm != 0x00002B63 || (p1 != -1 && (p1 < 0 || p1 > 2)) || (p2 != -1 && (p2 < 0 || p2 > 3)) || (p3 != -1 && p3 != 0 && p3 != 2)
                     || (p4 != -1 && (p4 < 0 || p4 > 2)) || (p5 != -1 && p5 != 0 && p5 != 1)){
                     err = 4;
                     break;
                  }
                  if(p1 != -1)
                     cnmi_mode = (byte)p1;
                  if(p2 != -1)
                     cnmi_mt = (byte)p2;
                  if(p3 != -1)
                     cnmi_bm = (byte)p3;
                  if(p4 != -1)
                     cnmi_ds = (byte)p4;
                  if(p5 != -1)
                     cnmi_bfr = (byte)p5;
                  break;
               case 0x6E6D693F: // AT+CNMI? (read New Message Indications to TE)
                  mm = mm == 0x00002B63 ? 0x0000002B : 0;
               case 0x636E6D69: // AT+CNMI (read New Message Indications to TE)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
                  rs = sb.append("+CNMI: ").append((char)(0x30 + cnmi_mode)).append(',').append((char)(0x30 + cnmi_mt)).append(','
                     ).append((char)(0x30 + cnmi_bm)).append(',').append((char)(0x30 + cnmi_ds)).append(',').append((char)(0x30 + cnmi_bfr)).toString();
                  break;
               case 0x65673D30: // AT+CREG=0 (disable network registration unsolicited result code)
               case 0x65673D31: // AT+CREG=1 (enable network registration unsolicited result code +CREG: <stat>)
                  if(mm != 0x002B6372){
                     err = 4;
                     break;
                  }
                  creg_nn = (byte)(jj & 0x0F);
                  break;
               case 0x65673D3F: // AT+CREG=? (query supported values)
                  if(mm != 0x002B6372){
                     err = 4;
                     break;
                  }
                  rs = "+CREG: (0-1)";
                  break;
               case 0x7265673F: // AT+CREG?
                  if(mm != 0x00002B63){
                     err = 4;
                     break;
                  }
                  rs = sb.append("+CREG: ").append((char)(0x30 + creg_nn)).append(',').append('1').toString();
                  break;
               case 0x2B2B6174: // +++AT (TIES)
                  if(mm != 0x0000002B){
                     err = 4;
                     break;
                  }
               case 0x002B2B2B: // +++ (terminate connection)
                  if(!banner)
                     break AT_LOOP;
                  break;
               default:
                  err = 4;
               }

               // Echo the AT response
               if(rs != null){
                  sb.setLength(0);
                  if(verb)
                     sb.append('\r').append('\n');
                  out.print(sb.append(rs).append('\r').append('\n').toString());
               }
               doST = true;
            }while(ii < ll);

            // Echo the AT status
            if(doST && !quiet){
               sb.setLength(0);
               if(verb || err == 1 || err == 2)
                  sb.append('\r').append('\n').append(serr[err]);
               else
                  sb.append(err);
               out.print(sb.append('\r').append('\n').toString());
            }
         }
      }catch(Exception ex){ /* NOOP */
      }finally{
         if(in != null)
            try{
               in.close();
            }catch(Exception exc){ /* NOOP */ }
      }
   }

   // Parse int from string
   static final int s2i(String str){
      int cc, ii, jj, rr = 0;
      if(str != null)
         for(ii = 0, jj = str.length(); ii < jj;)
            if((cc = str.charAt(ii++)) >= 0x30 && cc <= 0x39)
               rr = rr * 10 + cc - 0x30;
      return rr;
   }

   @Override
   public final void run(){
      int lww = ww, lmaxcon = maxcon;
      ServerSocket ss = null;
      int ii, ll;
      List<W> lths = new ArrayList<W>(lww << 1);
      for(ii = 0; ii < lww; ii++){
         W wk = new W(null);
         new Thread(wk).start();
         lths.add(wk);
      }
      ths = lths;
      while(true){
         try{
            ss = new ServerSocket(port);
            while(true){
               Socket sock = null;
               try{
                  request = false;
                  sock = ss.accept();
                  request = true;
                  conn++;
                  ii = 5; // wait 5 sec at most
                  while(true)
                     synchronized(lths){
                        if((ll = lths.size()) == 0){
                           if(lmaxcon > 0 && conn > lmaxcon){ // Either wait for a thread to become available or spawn a new one
                              if(ii > 0){
                                 if(ii-- == 5)
                                    System.err.print("Waiting for a worker\r\n");
                                 try{
                                    lths.wait(1000);
                                 }catch(InterruptedException ex){ /* NOOP */ }
                                 continue;
                              }
                              try{
                                 sock.close();
                              }catch(Exception ex){ /* NOOP */ }
                              System.err.print("Max. connections exceeded, connection dropped\r\n");
                              break;
                           }
                           new Thread(new W(sock)).start();
                        }else
                           lths.remove(ll - 1).go(sock);
                        break;
                     }
               }catch(Exception ex){
                  ex.printStackTrace();
               }
            }
         }catch(Exception ex){
            ex.printStackTrace();
         }finally{
            if(ss != null)
               try{
                  ss.close();
               }catch(Exception ex){ /* NOOP */ }
            ss = null;
         }
         try{
            Thread.sleep(5000); // wait 5 sec before retrying listening
         }catch(InterruptedException ex){ /* NOOP */ }
      }
   }

   // The worker class for the AT server thread pool.
   final class W implements Runnable{
      Socket sock;

      public W(Socket sock){ this.sock = sock; }

      synchronized final void go(Socket sock){
         this.sock = sock;
         notify();
      }

      @Override
      public final void run(){
         while(true){
            if(sock == null)
               synchronized(this){
                  while(sock == null)
                     try{
                        wait(1000);
                     }catch(InterruptedException ex){ /* NOOP */ }
               }

            // Handle an incoming AT session
            Socket lsock = sock;
            InputStream lis = null;
            OutputStream los = null;
            try{
               lsock.setSoTimeout(60000);
               lsock.setTcpNoDelay(true);
               lis = lsock.getInputStream();
               los = lsock.getOutputStream();
               doLoop(new InputStreamReader(lis), new PrintStream(los, true), false);
            }catch(Exception ex){ /* NOOP */
            }finally{
               lis = null;
               los = null;
               try{
                  lsock.shutdownInput(); // discard any unread headers
               }catch(Exception ex){ /* NOOP */ }
               try{
                  lsock.close();
               }catch(Exception ex){ /* NOOP */ }
            }
            sock = lsock = null;
            List<W> lths = AT.ths;
            synchronized(lths){
               AT.conn--;
               if(!AT.request && lths.size() >= AT.ww)
                  break; // free extra thread if no request pending
               lths.add(this);
               lths.notify();
            }
         }
      }
   }
}
