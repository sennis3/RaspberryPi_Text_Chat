
import java.net.*;
import java.io.*;
import java.util.*;
import com.pi4j.wiringpi.Gpio;
import com.pi4j.wiringpi.Lcd;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.RaspiPin;

/*
This class is a client that connects to the server.
To make displaying to the LCD easier, it will follow
a state switch style structure.
States:
1. Menu
2. View Messages
3. Reply (Multiple Choice)
4. Reply (Write your own)
5. Connected Clients List
*/

public class Client {

  int lcdHandle; // the handle to control calling the lcd
  private ArrayList<Message> messageList;
  private ArrayList<Integer> connectedClients;
  private final String[] quickReplyList = {"Hello", "Goodbye", "Yes","No", "How are you?","OKAY", "Can't talk", "Call me", "Where are you?", "I love you", "Talk Later",
                                            "Hmm...","Real Talk","Please Explain", "TTYL", "LOL", "LMAO",
                                          }; 
  String[] characters = {" ", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",
  "W", "X", "Y", "Z", ".", "!", "?"};
  public final static int LCD_ROWS = 4;
  public final static int LCD_COLUMNS = 20;
  public final static int LCD_BITS = 4;

  GpioController gpio;

  // provision gpio pin #02 as an input pin with its internal pull down resistor
  // enabled
  GpioPinDigitalInput upButton;
  GpioPinDigitalInput downButton;
  GpioPinDigitalInput leftButton;
  GpioPinDigitalInput rightButton;
  GpioPinDigitalInput selectButton;

  //Object that controlls all networking
  public NetConnection connection;

  Client(int port, String ip) {
    this.messageList = new ArrayList<Message>();
    this.connectedClients = new ArrayList<Integer>();
    this.connection = new NetConnection(port, ip);
  }

  /*
   * Inits the client connection to the server and inits the LCD and buttons
   */
  private boolean initClient() {
    /* Connect to the server */


    /* Set up the LCD Interface */

    // setup wiringPi
    if (Gpio.wiringPiSetup() == -1) {
      System.out.println(" ==>> GPIO SETUP FAILED");
      return false;
    }

    // initialize LCD
    lcdHandle = Lcd.lcdInit(LCD_ROWS, // number of row supported by LCD
        LCD_COLUMNS, // number of columns supported by LCD
        LCD_BITS, // number of bits used to communicate to LCD
        11, // LCD RS pin
        10, // LCD strobe pin
        0, // LCD data bit 1
        1, // LCD data bit 2
        2, // LCD data bit 3
        3, // LCD data bit 4
        0, // LCD data bit 5 (set to 0 if using 4 bit communication)
        0, // LCD data bit 6 (set to 0 if using 4 bit communication)
        0, // LCD data bit 7 (set to 0 if using 4 bit communication)
        0); // LCD data bit 8 (set to 0 if using 4 bit communication)

    // verify initialization
    if (lcdHandle == -1) {
      System.out.println(" ==>> LCD INIT FAILED");
      return false;
    }

    this.gpio = GpioFactory.getInstance();

    // provision gpio pin #02 as an input pin with its internal pull down resistor
    // enabled
    this.upButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_23, PinPullResistance.PULL_DOWN);
    this.downButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_22, PinPullResistance.PULL_DOWN);
    this.leftButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_26, PinPullResistance.PULL_DOWN);
    this.rightButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_27, PinPullResistance.PULL_DOWN);
    this.selectButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_21, PinPullResistance.PULL_DOWN);

    // set shutdown state for this input pin
    upButton.setShutdownOptions(true);
    downButton.setShutdownOptions(true);
    leftButton.setShutdownOptions(true);
    rightButton.setShutdownOptions(true);
    selectButton.setShutdownOptions(true);

    return true;
  }

  /*
   * This state displays the menu to the LCD It should have button listeners for
   * going up and down the menu on the LCD
   */
  private void menuState(boolean state) throws InterruptedException {
    int cursorXLoc = 0;
    int cursorYLoc = 1;
    boolean debounceCheck1;
    boolean debounceCheck2;
    boolean upButtonState = false;
    boolean downButtonState = false;
    boolean selectButtonState = state;

    Lcd.lcdCursor(lcdHandle, 0);
    Lcd.lcdCursorBlink(lcdHandle, 0);

    // Loop for writing text to LCD
    while (true) {

      // Debounce check if the upButton was pressed
      debounceCheck1 = upButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = upButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != upButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc > 1) // Ensure the cursor doesn't go off screen
              cursorYLoc--;
          }
        }
        upButtonState = debounceCheck1;
      }

      // Debounce check if the downButton was pressed
      debounceCheck1 = downButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = downButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != downButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc < 3) // Ensure the cursor doesn't go off screen
              cursorYLoc++;
          }
        }
        downButtonState = debounceCheck1;
      }

      
      // Debounce check if the downButton was pressed 
      debounceCheck1 = selectButton.isHigh(); 
      Thread.sleep(10); 
      debounceCheck2 = selectButton.isHigh(); 
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference 
        if (debounceCheck1 != selectButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            Lcd.lcdClear(lcdHandle); 
            if(cursorYLoc == 1)
              callNewState("MESSAGES"); 
            else if (cursorYLoc == 2)
              clientListState();
            else if (cursorYLoc == 3)
              clientListState();
            break; 
          } 
        }
        selectButtonState = debounceCheck1; 
      }
      

      String selectionArrow = ">";
      String readMessage = "(1) Read Messages ";
      String sendMessage = "(2) Send Message ";
      String viewClients = "(3) View Clients ";

      // This block of code formats our current selection
      if (cursorYLoc == 1)
        readMessage = selectionArrow.concat(readMessage);
      else if (cursorYLoc == 2)
        sendMessage = selectionArrow.concat(sendMessage);
      else if (cursorYLoc == 3)
        viewClients = selectionArrow.concat(viewClients);

      Lcd.lcdPosition(lcdHandle, 0, 0);
      Lcd.lcdPuts(lcdHandle, "MENU                ");

      Lcd.lcdPosition(lcdHandle, 0, 1);
      Lcd.lcdPuts(lcdHandle, readMessage);

      Lcd.lcdPosition(lcdHandle, 0, 2);
      Lcd.lcdPuts(lcdHandle, sendMessage);

      Lcd.lcdPosition(lcdHandle, 0, 3);
      Lcd.lcdPuts(lcdHandle, viewClients);

      Lcd.lcdPosition(lcdHandle, 0, cursorYLoc);

      Thread.sleep(100);
    } // End of While loop
  }

  private void clientListState() throws InterruptedException{
    int cursorYLoc = 1;
    int topArrayLocation = 0; //The top of the screen lists this reply option
    int bottomArrayLocation = 2; //The bottom of the screen lists this reply option
    boolean debounceCheck1;
    boolean debounceCheck2;
    boolean upButtonState = false;
    boolean downButtonState = false;
    boolean leftButtonState = false;
    boolean selectButtonState = true; //this is true because we enter this state by the selectButton being HIGH

    Lcd.lcdCursor(lcdHandle, 0);
    Lcd.lcdCursorBlink(lcdHandle, 0);

    while (true) {

      //Left button sends us back to the menu
      debounceCheck1 = leftButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = leftButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != leftButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            menuState(true);
            Lcd.lcdClear(lcdHandle); 
            break;
          }
        }
        leftButtonState = debounceCheck1;
      }

      // Debounce check if the upButton was pressed
      debounceCheck1 = upButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = upButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != upButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc > 1 ){ // Ensure the cursor doesn't go off screen
              cursorYLoc--;
            }
            else if (cursorYLoc == 1 && topArrayLocation > 0){ //Moves the displays options up
              bottomArrayLocation--;
              topArrayLocation--;
            }   
            Lcd.lcdClear(lcdHandle);
          }
        }
        upButtonState = debounceCheck1;
      }

      // Debounce check if the downButton was pressed
      debounceCheck1 = downButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = downButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != downButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc < 3){ // Ensure the cursor doesn't go off screen
              cursorYLoc++;
            }
            else if (cursorYLoc == 3 && bottomArrayLocation < (connectedClients.size() - 1)){ //Moves the displayed options down
              bottomArrayLocation++;
              topArrayLocation++;
            }
            Lcd.lcdClear(lcdHandle);
          }
        }
        downButtonState = debounceCheck1;
      }

      
      // Debounce check if the selectButton was pressed 
      debounceCheck1 = selectButton.isHigh(); 
      Thread.sleep(10); 
      debounceCheck2 = selectButton.isHigh(); 
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference 
        if (debounceCheck1 != selectButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            Lcd.lcdClear(lcdHandle); 
            //TODO Add in options for custom message
            if(cursorYLoc == 1){ //Pick our client based on the y cursors location
              if(connectedClients.size() > 0){
                clientOptions(connectedClients.get(topArrayLocation));
              }
            }
            else if (cursorYLoc == 2){
              if(connectedClients.size() > 1){
                clientOptions(connectedClients.get(topArrayLocation + 1));
              }
            }
            else if (cursorYLoc == 3){
              if(connectedClients.size() > 2){
                clientOptions(connectedClients.get(bottomArrayLocation));
              }
            }
            break; 
          } 
        }
        selectButtonState = debounceCheck1; 
      }

      String selectionArrow = ">";
      String topMessage = "--empty--";
      String middleMessage = "--empty--";
      String thirdMessage = "--empty--";

      if(connectedClients.size() > 0){
        topMessage = "(" + Integer.toString(topArrayLocation + 1) + ") client #" + Integer.toString(connectedClients.get(topArrayLocation));
      }
      if(connectedClients.size() > 1){
        middleMessage = "(" + Integer.toString(topArrayLocation + 2) + ") client #" + Integer.toString(connectedClients.get(topArrayLocation + 1));
      }
      if(connectedClients.size() > 2){
        thirdMessage = "(" + Integer.toString(bottomArrayLocation + 1) + ") client #" + Integer.toString(connectedClients.get(bottomArrayLocation));
      }

      // This block of code formats our current selection
      if (cursorYLoc == 1)
        topMessage = selectionArrow.concat(topMessage);
      else if (cursorYLoc == 2)
        middleMessage = selectionArrow.concat(middleMessage);
      else if (cursorYLoc == 3)
        thirdMessage = selectionArrow.concat(thirdMessage);

      Lcd.lcdPosition(lcdHandle, 0, 0);
      Lcd.lcdPuts(lcdHandle, "Clients:");

      Lcd.lcdPosition(lcdHandle, 0, 1);
      Lcd.lcdPuts(lcdHandle, topMessage);

      Lcd.lcdPosition(lcdHandle, 0, 2);
      Lcd.lcdPuts(lcdHandle, middleMessage);

      Lcd.lcdPosition(lcdHandle, 0, 3);
      Lcd.lcdPuts(lcdHandle, thirdMessage);

      Lcd.lcdPosition(lcdHandle, 0, cursorYLoc);

      Thread.sleep(100);
    } // End of While loop
  }

  /*
   * This state displays the messages to the LCD. The user can use the buttons to
   * flip through their received messages
   */
  private void messagesState() throws InterruptedException {
    boolean upButtonState = false;
    boolean downButtonState = false;
    boolean leftButtonState = false;
    boolean selectButtonState = true; //this is true because we enter this state by the selectButton being HIGH
    boolean debounceCheck1;
    boolean debounceCheck2;
    int currentMessageIndex = 0;

    Lcd.lcdCursor(lcdHandle, 0);
    Lcd.lcdCursorBlink(lcdHandle, 0);

    // Loop for printing to the LCD
    while (true) {

      //Left button sends us back to the menu
      debounceCheck1 = leftButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = leftButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != leftButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            Lcd.lcdClear(lcdHandle); 
            menuState(true);
            break;
          }
        }
        leftButtonState = debounceCheck1;
      }

      // Debounce check if the upButton was pressed
      debounceCheck1 = upButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = upButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != upButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (currentMessageIndex < messageList.size() - 1){
              currentMessageIndex++;
              Lcd.lcdClear(lcdHandle);
            }
          }
        }
        upButtonState = debounceCheck1;
      }
      // Left button displays the previous message
      debounceCheck1 = downButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = downButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != downButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (currentMessageIndex > 0)
              currentMessageIndex--;
              Lcd.lcdClear(lcdHandle);
          }
        }
        downButtonState = debounceCheck1;
      }

      if (messageList.size() == 0) { // If we have no messages display that
        Lcd.lcdPosition(lcdHandle, 0, 1);
        Lcd.lcdPuts(lcdHandle, "         NO         ");
        Lcd.lcdPosition(lcdHandle, 0, 2);
        Lcd.lcdPuts(lcdHandle, "      MESSAGES      ");
      } 
      else { //Else display the messges
        // Debounce check if the select Button was pressed 
        debounceCheck1 = selectButton.isHigh(); 
        Thread.sleep(10); 
        debounceCheck2 = selectButton.isHigh(); 
        if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference 
          if (debounceCheck1 != selectButtonState) { // If they're not equal the button's state has changed
            if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
              Lcd.lcdClear(lcdHandle);
              messageOptions(currentMessageIndex); //Goes to the options for the selected message
              break; 
            } 
          }
          selectButtonState = debounceCheck1; 
        }

        // Print the current message's index out of our total received messages
        String top = String.format("%03d/%03d FROM: #%d", currentMessageIndex + 1, messageList.size(), messageList.get(currentMessageIndex).senderID);
        Lcd.lcdPosition(lcdHandle, 0, 0);
        Lcd.lcdPuts(lcdHandle, top);
        // Fetch the current message to be printed
        String currentMessage = messageList.get(currentMessageIndex).message;
        if (currentMessage.length() > 20) { // If the message is greater than 10 we have to split it
          Lcd.lcdPosition(lcdHandle, 0, 1);
          Lcd.lcdPuts(lcdHandle, currentMessage.substring(0, 20)); // Print the first half of the message
          Lcd.lcdPosition(lcdHandle, 0, 2);
          Lcd.lcdPuts(lcdHandle, currentMessage.substring(20, currentMessage.length())); // Print the second half of the message
        } else { // Else, print the message
          Lcd.lcdPosition(lcdHandle, 0, 1);
          Lcd.lcdPuts(lcdHandle, currentMessage);
        }
      }
      Thread.sleep(100);
    } // End of while loop 
  }

  /*
  * This state lists the options for a selected message
  * Delete, Reply, Quick Reply
  */
  private void messageOptions(int messageIndex) throws InterruptedException{
    int cursorYLoc = 1;
    boolean debounceCheck1;
    boolean debounceCheck2;
    boolean upButtonState = false;
    boolean leftButtonState = false;
    boolean downButtonState = false;
    boolean selectButtonState = true; //this is true because we enter this state by the selectButton being HIGH

    Lcd.lcdCursor(lcdHandle, 0);
    Lcd.lcdCursorBlink(lcdHandle, 0);

    // Loop for writing text to LCD
    while (true) {

      //Left button sends us back to the menu
      debounceCheck1 = leftButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = leftButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != leftButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            menuState(true);
            Lcd.lcdClear(lcdHandle); 
            break;
          }
        }
        leftButtonState = debounceCheck1;
      }

      // Debounce check if the upButton was pressed
      debounceCheck1 = upButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = upButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != upButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc > 1) // Ensure the cursor doesn't go off screen
              cursorYLoc--;
          }
        }
        upButtonState = debounceCheck1;
      }

      // Debounce check if the downButton was pressed
      debounceCheck1 = downButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = downButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != downButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc < 3) // Ensure the cursor doesn't go off screen
              cursorYLoc++;
          }
        }
        downButtonState = debounceCheck1;
      }

      
      // Debounce check if the downButton was pressed 
      debounceCheck1 = selectButton.isHigh(); 
      Thread.sleep(10); 
      debounceCheck2 = selectButton.isHigh(); 
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference 
        if (debounceCheck1 != selectButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            Lcd.lcdClear(lcdHandle); 
            //callNewState("MESSAGES"); //TODO add in options for button press on choice
            if(cursorYLoc == 1){
              messageList.remove(messageIndex); //delete the selected message
              callNewState("MESSAGES"); //Go back to the messages page
            }
            else if(cursorYLoc == 2){
              quickReplyState(messageList.get(messageIndex).senderID); //Go to quick reply options
            }
            else if (cursorYLoc == 3){
              customReplyState(messageList.get(messageIndex).senderID);
            }
            break; 
          } 
        }
        selectButtonState = debounceCheck1; 
      }
      

      String selectionArrow = ">";
      String deleteMessage = "(1) Delete ";
      String quickMessage = "(2) Quick Reply ";
      String longMessage = "(3) Reply ";

      // This block of code formats our current selection
      if (cursorYLoc == 1)
        deleteMessage = selectionArrow.concat(deleteMessage);
      else if (cursorYLoc == 2)
        quickMessage = selectionArrow.concat(quickMessage);
      else if (cursorYLoc == 3)
        longMessage = selectionArrow.concat(longMessage);

      Lcd.lcdPosition(lcdHandle, 0, 0);
      Lcd.lcdPuts(lcdHandle, "OPTIONS             ");

      Lcd.lcdPosition(lcdHandle, 0, 1);
      Lcd.lcdPuts(lcdHandle, deleteMessage);

      Lcd.lcdPosition(lcdHandle, 0, 2);
      Lcd.lcdPuts(lcdHandle, quickMessage);

      Lcd.lcdPosition(lcdHandle, 0, 3);
      Lcd.lcdPuts(lcdHandle, longMessage);

      Lcd.lcdPosition(lcdHandle, 0, cursorYLoc);

      Thread.sleep(100);
    } // End of While loop
  }

  /*
   * This class just adds in fake messages for the purpose of testing The
   * messageState function
   * TODO delete
   */
  private void messageDemo() {
    Message msg1 = new Message();
    msg1.message = "Hey bill welcome to New Hampshire";
    msg1.senderID = 13;

    Message msg2 = new Message();
    msg2.message = "Saturdays are for all people";
    msg2.senderID = 19;

    Message msg3 = new Message();
    msg3.message = "I am very tired";
    msg3.senderID = 1;

    Message msg4 = new Message();
    msg4.message = "I wish I was Hamtaro";
    msg4.senderID = 3;

    connectedClients.add(1);
    connectedClients.add(3);

    messageList.add(msg1);
    messageList.add(msg2);
    messageList.add(msg3);
    messageList.add(msg4);
  }

  //This state will list options for a quick reply
  //Selecting the message will then send it to the server
  private void quickReplyState(int clientID) throws InterruptedException{
    int cursorYLoc = 1;
    int topArrayLocation = 0; //The top of the screen lists this reply option
    int bottomArrayLocation = 2; //The bottom of the screen lists this reply option
    boolean debounceCheck1;
    boolean debounceCheck2;
    boolean upButtonState = false;
    boolean downButtonState = false;
    boolean selectButtonState = true; //this is true because we enter this state by the selectButton being HIGH

    while (true) {

      // Debounce check if the upButton was pressed
      debounceCheck1 = upButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = upButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != upButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc > 1){ // Ensure the cursor doesn't go off screen
              cursorYLoc--;
              Lcd.lcdClear(lcdHandle);
            }
            else if (cursorYLoc == 1 && topArrayLocation > 0){ //Moves the displays options up
              bottomArrayLocation--;
              topArrayLocation--;
              Lcd.lcdClear(lcdHandle);
            }   
          }
        }
        upButtonState = debounceCheck1;
      }

      // Debounce check if the downButton was pressed
      debounceCheck1 = downButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = downButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != downButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc < 3){ // Ensure the cursor doesn't go off screen
              cursorYLoc++;
              Lcd.lcdClear(lcdHandle);
            }
            else if (cursorYLoc == 3 && bottomArrayLocation < (quickReplyList.length - 1)){ //Moves the displayed options down
              bottomArrayLocation++;
              topArrayLocation++;
              Lcd.lcdClear(lcdHandle);
            }
          }
        }
        downButtonState = debounceCheck1;
      }

      
      // Debounce check if the selectButton was pressed 
      debounceCheck1 = selectButton.isHigh(); 
      Thread.sleep(10); 
      debounceCheck2 = selectButton.isHigh(); 
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference 
        if (debounceCheck1 != selectButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            Lcd.lcdClear(lcdHandle); 
            if(cursorYLoc == 1){ //Pick our quick reply based on the y cursors location
              String messageString = quickReplyList[topArrayLocation]; //Get the string for our quick reply
              sendMessage(messageString, clientID);
            }
            else if (cursorYLoc == 2){
              String messageString = quickReplyList[topArrayLocation + 1]; //Get the string for our quick reply
              sendMessage(messageString, clientID);
            }
            else if (cursorYLoc == 3){
              String messageString = quickReplyList[bottomArrayLocation]; //Get the string for our quick reply
              sendMessage(messageString, clientID);
            }
            menuState(true);
            break; 
          } 
        }
        selectButtonState = debounceCheck1; 
      }

      String selectionArrow = ">";
      String topMessage = "(" + Integer.toString(topArrayLocation + 1) + ")" + quickReplyList[topArrayLocation];
      String middleMessage = "(" + Integer.toString(topArrayLocation + 2) + ")" + quickReplyList[topArrayLocation + 1];
      String thirdMessage = "(" + Integer.toString(bottomArrayLocation + 1) + ")" + quickReplyList[bottomArrayLocation];

      // This block of code formats our current selection
      if (cursorYLoc == 1)
        topMessage = selectionArrow.concat(topMessage);
      else if (cursorYLoc == 2)
        middleMessage = selectionArrow.concat(middleMessage);
      else if (cursorYLoc == 3)
        thirdMessage = selectionArrow.concat(thirdMessage);

      Lcd.lcdPosition(lcdHandle, 0, 0);
      Lcd.lcdPuts(lcdHandle, "Choose a reply");

      Lcd.lcdPosition(lcdHandle, 0, 1);
      Lcd.lcdPuts(lcdHandle, topMessage);

      Lcd.lcdPosition(lcdHandle, 0, 2);
      Lcd.lcdPuts(lcdHandle, middleMessage);

      Lcd.lcdPosition(lcdHandle, 0, 3);
      Lcd.lcdPuts(lcdHandle, thirdMessage);

      Lcd.lcdPosition(lcdHandle, 0, cursorYLoc);

      Thread.sleep(100);
    } // End of While loop
  }

  private void clientOptions(int clientID) throws InterruptedException{
    int cursorYLoc = 1;
    boolean debounceCheck1;
    boolean debounceCheck2;
    boolean upButtonState = false;
    boolean downButtonState = false;
    boolean selectButtonState = true; //this is true because we enter this state by the selectButton being HIGH

    Lcd.lcdCursor(lcdHandle, 0);
    Lcd.lcdCursorBlink(lcdHandle, 0);

    // Loop for writing text to LCD
    while (true) {

      // Debounce check if the upButton was pressed
      debounceCheck1 = upButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = upButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != upButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc > 1) // Ensure the cursor doesn't go off screen
              cursorYLoc--;
          }
        }
        upButtonState = debounceCheck1;
      }

      // Debounce check if the downButton was pressed
      debounceCheck1 = downButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = downButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != downButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if (cursorYLoc < 2) // Ensure the cursor doesn't go off screen
              cursorYLoc++;
          }
        }
        downButtonState = debounceCheck1;
      }

      
      // Debounce check if the downButton was pressed 
      debounceCheck1 = selectButton.isHigh(); 
      Thread.sleep(10); 
      debounceCheck2 = selectButton.isHigh(); 
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference 
        if (debounceCheck1 != selectButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            Lcd.lcdClear(lcdHandle); 
            if(cursorYLoc == 1){
              quickReplyState(clientID);
            }
            else if(cursorYLoc == 2){
              customReplyState(clientID);
            }
            break; 
          } 
        }
        selectButtonState = debounceCheck1; 
      }
      

      String selectionArrow = ">";
      String quickMessage = "(1) Quick Message ";
      String longMessage = "(2) Message ";

      // This block of code formats our current selection
      if (cursorYLoc == 1)
        quickMessage = selectionArrow.concat(quickMessage);
      else if (cursorYLoc == 2)
        longMessage = selectionArrow.concat(longMessage);

      Lcd.lcdPosition(lcdHandle, 0, 0);
      Lcd.lcdPuts(lcdHandle, "OPTIONS             ");

      Lcd.lcdPosition(lcdHandle, 0, 1);
      Lcd.lcdPuts(lcdHandle, quickMessage);

      Lcd.lcdPosition(lcdHandle, 0, 2);
      Lcd.lcdPuts(lcdHandle, longMessage);

      Lcd.lcdPosition(lcdHandle, 0, cursorYLoc);

      Thread.sleep(100);
    } // End of While loop
  }

  //This is the state for wiriting a custom message
  private void customReplyState(int clientID) throws InterruptedException {
    int cursorXLoc = 0;
    int cursorYLoc = 1;
    int messageIndex = 0; //The currently selected character index in our message
    boolean debounceCheck1;
    boolean debounceCheck2;
    boolean selectingCharacter = false; //If true, we are in character selection mode
    boolean upButtonState = false;
    boolean downButtonState = false;
    boolean leftButtonState = false;
    boolean rightButtonState = false;
    boolean selectButtonState = true; //this is true because we enter this state by the selectButton being HIGH

    //The message is contained by a string of ints, which is used to map out each character
    int[] message = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    Lcd.lcdPosition(lcdHandle, 0, 1);
    Lcd.lcdCursor(lcdHandle, 1);
    Lcd.lcdCursorBlink(lcdHandle, 1);

    while (true){
      // Debounce check if the upButton was pressed
      debounceCheck1 = upButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = upButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != upButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if(!selectingCharacter){ //We can only navigate the UI if we are not selecting a character
              if (cursorYLoc == 2){ // Ensure the cursor doesn't go off screen
                cursorYLoc--;
              }
              else if(cursorYLoc == 3){
                cursorYLoc = 2;
                cursorXLoc = 0;
              }
            }
            else { //Go up a position in the character array
              if(message[messageIndex] == (characters.length - 1)){ //ensure no out of bounds errors
                message[messageIndex] = 0;
              }
              else {
                message[messageIndex]++;
              }
            }
            Lcd.lcdClear(lcdHandle); 
          }
        }
        upButtonState = debounceCheck1;
      }

      // Debounce check if the downButton was pressed
      debounceCheck1 = downButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = downButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != downButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if(!selectingCharacter){ //We can only navigate the UI if we are not selecting a character
              if (cursorYLoc == 1){ // Ensure the cursor doesn't go off screen
                cursorYLoc++;
              }
              else if(cursorYLoc == 2){
                cursorYLoc++;
                cursorXLoc = 0;
              }
            }
            else{ //go down a position in the characters array
              if(message[messageIndex] == 0){ //ensure no out of bounds errors
                message[messageIndex] = characters.length - 1;
              }
              else {
                message[messageIndex]--;
              }
            }
            Lcd.lcdClear(lcdHandle); 
          }
        }
        downButtonState = debounceCheck1;
      }

      // Debounce check if the downButton was pressed 
      debounceCheck1 = selectButton.isHigh(); 
      Thread.sleep(10); 
      debounceCheck2 = selectButton.isHigh(); 
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference 
        if (debounceCheck1 != selectButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            Lcd.lcdClear(lcdHandle); 
            if(!selectingCharacter){ //If we are not selecting a character, we enter character selection mode
              if(cursorYLoc == 1){ //Check if we're currently on our message area
                selectingCharacter = true; //Boolean to detect character selection mode
                messageIndex = cursorXLoc;
              }
              else if(cursorYLoc == 2){ //Check if we're currently on our message area
                selectingCharacter = true; //Boolean to detect character selection mode
                messageIndex = cursorXLoc + 20;
              }
              else if(cursorYLoc == 3){ //Code for selecting a bottom option
                if(cursorXLoc == 0){
                  callNewState("MENU");
                  break;
                }
                else{
                  //TODO send message code
                  System.out.println("Sending custom message");
                  String msgString = intArrayToString(message);
                  sendMessage(msgString, clientID);
                  callNewState("MENU");
                  break;
                }
              }
            }
            else{
              selectingCharacter = false;
            }
          } 
        }
        selectButtonState = debounceCheck1; 
      }

      debounceCheck1 = rightButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = rightButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != rightButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if(!selectingCharacter){ //We can only navigate the UI if we are not selecting a character
              if (cursorXLoc < 19 && cursorYLoc != 3) // Ensure the cursor doesn't go off screen
                cursorXLoc++;
              else if(cursorYLoc == 3){
                if(cursorXLoc == 0){
                  cursorXLoc = 15;
                }
              }
            }
            Lcd.lcdClear(lcdHandle); 
          }
        }
        rightButtonState = debounceCheck1;
      }

      debounceCheck1 = leftButton.isHigh();
      Thread.sleep(10);
      debounceCheck2 = leftButton.isHigh();
      if (debounceCheck1 == debounceCheck2) { // If they're equal we know there was no interference
        if (debounceCheck1 != leftButtonState) { // If they're not equal the button's state has changed
          if (debounceCheck1 == true) { // True is the pi4j equiv of Arduino's HIGH
            if(!selectingCharacter){ //We can only navigate the UI if we are not selecting a character
              if (cursorXLoc > 0 && cursorYLoc != 3) // Ensure the cursor doesn't go off screen
                cursorXLoc--;
              else if(cursorYLoc == 3){
                if(cursorXLoc == 15){
                  cursorXLoc = 0;
                }
              }
            }
            Lcd.lcdClear(lcdHandle); 
          }
        }
        leftButtonState = debounceCheck1;
      }

      String selectionArrow = ">";
      String bottomOptions = " CANCEL         SEND";
      String topHeader = "Message: ";

      Lcd.lcdPosition(lcdHandle, 0, 0);
      Lcd.lcdPuts(lcdHandle, topHeader);

      Lcd.lcdPosition(lcdHandle, 0, 3);
      Lcd.lcdPuts(lcdHandle, bottomOptions);

      if(cursorYLoc == 3 && cursorXLoc == 0){
        Lcd.lcdPosition(lcdHandle, 0, 3);
        Lcd.lcdPuts(lcdHandle, ">");
      }
      else if(cursorYLoc == 3 && cursorXLoc == 15){
        Lcd.lcdPosition(lcdHandle, 15, 3);
        Lcd.lcdPuts(lcdHandle, ">");
      }

      //Print out the current state of the custom message
      for(int i = 0; i < 20; i++){
        Lcd.lcdPosition(lcdHandle, i, 1);
        Lcd.lcdPuts(lcdHandle, characters[message[i]]);
      }
      for(int i = 0; i < 20; i++){
        Lcd.lcdPosition(lcdHandle, i, 2);
        Lcd.lcdPuts(lcdHandle, characters[message[i + 20]]);
      }

      Lcd.lcdPosition(lcdHandle, cursorXLoc, cursorYLoc);
    }
  }

  //Helper function for writing a custom message
  private String intArrayToString(int[] intMsg){
    String message = "";
    for(int i = 0; i < intMsg.length; i++)
      message = message + characters[intMsg[i]];
    return message;
  }

  // Calls a new state based on the string passed.
  // MENU = Menu
  // MESSAGES = View Messages
  // MULT_REPLY = Reply (Multiple Choice)
  // TYPE_REPLY = Reply (Write your own)
  // LIST = Connected Clients List
  private void callNewState(String newState) throws InterruptedException {
    // TODO add more state cases
    switch (newState) {
    case "MENU":
      menuState(true);
      break;
    case "MESSAGES":
      messagesState();
      break;
    default:
      // menuState();
    }
  };

  private void sendMessage(String msg, int receiverID) {
    Message newMessage = new Message();
    newMessage.senderID = connection.ID;
    newMessage.receiverID = receiverID;
    newMessage.message = msg;
    newMessage.messageType = "MESSAGE";
    connection.sendMessage(newMessage);
  }

  class NetConnection extends Thread{
	
    Socket socketClient;
    ObjectOutputStream out;
    ObjectInputStream in;
    String ip;
    int port;
    int ID;
    
    NetConnection(int port, String ip){
      this.port = port;
      this.ip = ip;
    }
  
    void closeSocket() {
      try {
        socketClient.close();
      } catch (IOException e) {
        
      }
    }
    
    public void run() {
      
      try {
        socketClient= new Socket(ip, port);
          out = new ObjectOutputStream(socketClient.getOutputStream());
          in = new ObjectInputStream(socketClient.getInputStream());
          socketClient.setTcpNoDelay(true);
          System.out.println("Connected to server");
      }
      catch(Exception e) {
    	  System.out.println("Can't connect");
    	  return;
      }
      
      //Keep accepting new info
      while(true) {
        try {
          //Listen for data from the server
          Message input = (Message)in.readObject();
          System.out.println("Message Received");
          parseMessage(input);
        } 
        catch (Exception e) {
        	System.out.println("Lost connection");
        	break;
        }		
      }//End of while loop
    }//end of run
    
    public void sendMessage(Message output) {
      try {
        out.writeObject(output);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

      /*
      * Parses a message based on the message type and either adds it to the
      * messageList or if the message is an instruction from the server it calls a
      * function to do the instruction (e.g. "Update client list")
      */
    private void parseMessage(Message msg) {
    	System.out.println("Entered parse message");
      final String msgType = msg.messageType;

      // Switch statement to check message type
      switch (msgType) {
      case "INIT":
        System.out.println("Received Init");
        this.ID = msg.receiverID;
        System.out.println(ID);
        break;
      case "MESSAGE":
        synchronized(messageList){
          System.out.println("Message Received");
          messageList.add(msg);
          System.out.println(msg.message);
          break;
        }
      case "CLIENT_LIST":
        synchronized(connectedClients){
        	System.out.println("Enter client list update");
          connectedClients = msg.clientList;
          for(int i = 0; i < connectedClients.size(); i++)
            if(connectedClients.get(i) == this.ID)
              connectedClients.remove(i);
          System.out.println(connectedClients);
          break;
        }
      default:
        // Do nothing in the default
      }
    }
  }

  public static void main(final String args[]) throws InterruptedException {
    //TODO configure ip adress
    Client theClient = new Client(5555, "10.7.33.80");
    //theClient.messageDemo();
    if (theClient.initClient()) { // If initing the client is successful start the menu function
      System.out.println("Init correctly");
      theClient.connection.start();
      theClient.menuState(false);
    }
    System.out.println("Init failed");
  }
}

class Message implements Serializable {
  int senderID, receiverID;
  protected static final long serialVersionUID = 1112122200L;
  public String message;
  public String messageType;
  public ArrayList<Integer> clientList;
}
