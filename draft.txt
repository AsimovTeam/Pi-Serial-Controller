using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using System.IO.Ports;
using Modbus.Device;

namespace AsimovManagerLib
{


    // Cette classe s'exécute dans un thread pour ne pas retarder l'exécution du jeu vidéo associé.
    // Le jeu ne doit PAS attendre la fin de l'exécution des opérations, sinon, le jeu devient trop lent.
    // Chaque fonction appelée met simplement une variable à vrai ou ajuste une valeur.
    // Le prochain tour de boucle du thread exécute ensuite l'action à faire de façon
    // autonome au jeu.  
    // Comme l'exécution est asynchrone, il faut vérouiller (lock) nos variables avant
    // d'écrire dedans. 
    public class EcritureRS232
    {
        // Variables utiles au port et à la communication Modbus.
        private static IModbusSerialMaster master;
        private static SerialPort port = new SerialPort("COM3");

        // Constantes importantes aux actuateurs et aux registres. 
        private const int high = 1;
        private const int low = 0;
        private const int sleepTime = 70;
        private const byte actuateurGauche = 2;
        private const byte actuateurDroit = 1;
        private const float positionHome = 25.0f;

        private int vitesse = 20;
        private int acceleration = 20;

        // La position actuelle et l'état des actuateurs.
        private float positionGauche = positionHome;
        private float positionDroit =  positionHome;
        private bool ActuateursActif = false;

        // Série de booléens pour appeler ou non des fonctions
        private bool appelInitialise = false;
        private bool appelVitesse = false;
        private bool appelAcceleration = false;
        private bool appelGenerique = false;
        private bool appelStop = false;
        // Id d'une fonction générique appelée.
        private int id = 0;  
        // boolean qui arrête le thread
        private bool sortie = false;

        // Constructeur
        public EcritureRS232()
        {

        }

        // Méthode sur laquelle boucle le thread.  
        public void BoucleRS232()
        {
            Console.WriteLine("demarre actuateurs");
            // Démarrage des actuateurs avant d'amorcer la boucle.
            DemarreActuateurs();

            // Boucle infinie.  
            while (!sortie)
            {
                Console.WriteLine("while");
                // On appelle la bonne fonction si le booleen est levé.
                if (appelInitialise)
                    ResetActuateurs();
                if (appelAcceleration)
                    SetAcceleration();
                if (appelVitesse)
                    SetVitesse();
                if (appelGenerique)
                    ExecIndividualsFct();
                if (appelStop)
                    ArreteTout();

                // Si les actuateurs sont actifs, on écrit la position sur ceux-ci.
                if (ActuateursActif)
                {
                    Ecriture();
                    Console.WriteLine("Write");
                }
                // Sinon attente.
                else 
                {
                    Console.WriteLine("PAUSE INACTIF");
                    Thread.Sleep(500);
                }
                    
            }
        }

        public void IndividualsFct(int leId)
        {
            lock (this)
            {
                id = leId;
                appelGenerique = true;
            }
        }

        private void ResetActuateurs()
        {
            Reset(actuateurGauche);
            Reset(actuateurDroit);
            //SetModeHostDefault(actuateurGauche);
            //SetModeHostDefault(actuateurDroit);
            //Descendre(actuateurGauche);
            //Descendre(actuateurDroit);
            //Reset(actuateurGauche);
            //Reset(actuateurDroit);

            lock (this)
            {
                appelInitialise = false;
            }
        }

        public void Initialise()
        {
            lock (this)
            {
                appelInitialise = true;
            }
        }

        private void Ecriture()
        {
            AjouteMouvement(positionGauche, positionDroit);
        }


        public void SetPositionFromGames(float gauche, float droite)
        {
            lock (this)
            {
                positionGauche = gauche;
                positionDroit = droite;
            }
        }

        private static void Reset(byte slaveId)
        {
            master.WriteSingleRegister(slaveId, 4001, (ushort)1);
            Thread.Sleep(200);
        }

        public void Activate()
        {
            lock (this)
            {
                ActuateursActif = true;
            }
        }

        public void Deactivate()
        {
            lock (this)
            {
                ActuateursActif = false;
            }
        }

        public void Stop()
        {
            appelStop = true;
        }

        public void ArreteTout()
        {
            AjouteMouvement(1.0f, 1.0f);
            Thread.Sleep(1000);
            Deactivate();
            port.Close();
            lock (this)
            {
                sortie = true;
            }

        }

        // Méthode qui exécute les quelques fonctions génériques.  
        private void ExecIndividualsFct()
        {
            switch (id)
            {
                case 1:
                    // Ouverture du port
                    Activate();
                    break;
                case 2:
                    // Lecture des registres pour activer les actuateurs
                    master.ReadInputRegisters(actuateurDroit, 5, (ushort)1);
                    master.ReadInputRegisters(actuateurGauche, 5, (ushort)1);
                    break;
                case 3:
                    // Auto enabled maintained
                    master.WriteSingleRegister(actuateurDroit, 5100, (ushort)1);
                    master.WriteSingleRegister(actuateurGauche, 5100, (ushort)1); 
                    break;
                case 4:
                    // Set mode: host position
                    SetModeHostPosition(actuateurGauche);
                    SetModeHostPosition(actuateurDroit);
                    break;
                case 5:
                    // Level actuators to 25-25.
                    AjouteMouvement(positionHome, positionHome);
                    break;
                case 6:
                    // Deactivate actuators
                    Deactivate();
                    break;
                case 7:
                    // Mode default
                    SetModeHostDefault(actuateurDroit);
                    SetModeHostDefault(actuateurGauche);
                    break;
                case 8:
                    // Descendre les actuateurs de...
                    SetVitesse(10);
                    SetAcceleration(20);
                    Descendre(actuateurDroit);
                    Descendre(actuateurGauche);
                    break;
                case 9:
                    Reset(actuateurGauche);
                    Reset(actuateurDroit);
                    break;
                case 10:
                    Monter(actuateurGauche);
                    Monter(actuateurDroit);
                    break;
                case 11:
                    Home(actuateurGauche);
                    Home(actuateurDroit);
                    break;
                default:
                    break;
            }

            lock (this)
            {
                 appelGenerique = false;
            }
            Thread.Sleep(200);
        }

        private void DemarreActuateurs()
        {
            if (!ActuateursActif)
            {
                ModbusSerialRtuMasterOpenRS232Port();
                
                InitParameters(actuateurGauche);
                InitParameters(actuateurDroit);

                SetModeHostPosition(actuateurGauche);
                SetModeHostPosition(actuateurDroit);

                //Home(actuateurGauche);
                //Home(actuateurDroit);

                SetVitesse(vitesse);
                SetAcceleration(acceleration);

                //AJOUTER LE 26 FEV 2014 PAR SHANY JSD
                Activate();
            }
        }

        /// <summary>
        /// Simple Modbus serial RTU master write holding registers example.
        /// </summary>
        private void ModbusSerialRtuMasterOpenRS232Port()
        {

            if (!port.IsOpen)
            {
                port.Close();
                port.BaudRate = 19200;
                port.DataBits = 8;
                port.Parity = Parity.Even;
                port.StopBits = StopBits.One;
                port.Open();
            }
            

            Thread.Sleep(200);
            // create modbus master
            master = ModbusSerialMaster.CreateRtu(port);
            Thread.Sleep(200);
   
        }

        // Ajoute un mouvement  
        private void AjouteMouvement(float AvDroit, float AvGauche)
        {
            if (AvDroit > 49.0f)
                AvDroit = 49.0f;


                
            if (AvGauche > 49.0f)
                AvGauche = 49.0f;
            if (AvDroit < 1.0f)
                AvDroit = 1.0f;
            if (AvGauche < 1.0f)
                AvGauche = 1.0f;

            Write32BitRegister(actuateurGauche, 4304, AvGauche);
            Write32BitRegister(actuateurDroit, 4304, AvDroit);

        }

        private static void InitParameters(byte slaveId)
        {

            master.ReadInputRegisters(slaveId, 5, (ushort)1);
            Thread.Sleep(100);
            master.WriteSingleRegister(slaveId, 5100, (ushort)1);    // Auto Enabled Maintained.  p.5 du doc des registres. JSD
            Thread.Sleep(100);
        }

        public void SetVitesse(int laVitesse)
        {
            // Ajuste la vitesse du drive
            lock (this)
            {
                vitesse = laVitesse;
            }
            appelVitesse = true;
        }

        public void SetAcceleration(int lAcceleration)
        {
            // Ajuste l'acceleration du drive
            lock (this)
            {
                acceleration = lAcceleration;
            }
            appelAcceleration = true;
        }

        public void SetVitesse()
        {
            // Ajuste la vitesse du drive en jog fast
            ushort[] registers = new ushort[2];
            registers[high] = (ushort)((ushort)vitesse << 8);
            registers[low] = 0;
            master.WriteMultipleRegisters(actuateurGauche, 4306, registers);
            Thread.Sleep(100);
            master.WriteMultipleRegisters(actuateurDroit, 4306, registers);
            Thread.Sleep(100);
            appelVitesse = false;
        }

        public void SetAcceleration()
        {
            // Ajuste l'acceleration du drive
            ushort[] registers = new ushort[2];
            registers[high] = (ushort)((ushort)acceleration << 4);
            registers[low] = 0;
            master.WriteMultipleRegisters(actuateurGauche, 4308, registers);
            Thread.Sleep(100);
            master.WriteMultipleRegisters(actuateurDroit, 4308, registers);
            Thread.Sleep(100);
            appelAcceleration = false;
        }

        private void SetModeHostPosition(byte slaveId)
        {
            master.WriteSingleRegister(slaveId, 4302, (ushort)0);    //  Disabled flags pour permettre de changer de mode.
            Thread.Sleep(sleepTime);
            //master.WriteSingleRegister(slaveId, 5106, (ushort)5);    //  Default command mode:  OPMODE: Host Position
            //Thread.Sleep(SLEEPTIME);
            ushort cur = 20 << 7;
            master.WriteSingleRegister(slaveId, 4310, cur);   // Electrical current: Charge électrique en AMPERES.  20
            Thread.Sleep(sleepTime);
            master.WriteSingleRegister(slaveId, 4303, (ushort)5);    //  Host control command mode:  OPMODE: Host Position
            Thread.Sleep(sleepTime);
        }

        private void SetModeHostDefault(byte slaveId)
        {
            master.WriteSingleRegister(slaveId, 4303, (ushort)0);
            Thread.Sleep(sleepTime);
        }

        public static void Descendre(byte slaveId)
        {
            // Jog Negative
            master.WriteSingleRegister(slaveId, 4317, (ushort)32/*+64 (Jog Fast)*/);
            Thread.Sleep(sleepTime);
        }

        public static void Write32BitRegister(byte slaveId, ushort address, float value)
        {
            ushort[] data = new ushort[2];
            data[high] = (ushort)((int)value);
            //float nb1 = value - data[high];
            //int nb = Convert.ToInt16((double)(nb1 * Math.Pow(10, nb1.ToString().Length - 2)));
            //data[low] = (ushort) nb;
            //data[low] = (ushort)((double)(value - (int)value) << 16);
            data[low] = (ushort)0;
            master.WriteMultipleRegisters(slaveId, address, data);
            Thread.Sleep(sleepTime);
        }

        public static void Monter(byte slaveId)
        {
            // Jog Positive
            master.WriteSingleRegister(slaveId, 4317, (ushort)16/*+64 (Jog Fast)*/);
            Thread.Sleep(sleepTime);
        }

        public static void Home(byte slaveId)
        {
            // Home
            master.WriteSingleRegister(slaveId, 4317, (ushort)266);
            Thread.Sleep(sleepTime);
        }

        // Le code qui suit est le résultat de plusieurs mois d'essais erreur en 2010.
        // On le garde au cas ou...  Et à cause de sa valeur sentimentale.



        //public void ResetFromHostPosition(byte slaveId)
        //{
        //    // Voir JSD pour explications sur les derniers changements.
        //    // On passe en mode DEFEAULT et on RESET
        //    SetModeHostDefault(slaveId);
        //    Thread.Sleep(SLEEPTIME);
        //    Reset(slaveId);
        //    Thread.Sleep(SLEEPTIME);
        //    // On redescend les drives à leur position initiale (0)
        //    //Descendre(slaveId);
        //    //Thread.Sleep(SLEEPTIME);
        //    // On fait le RESET en mode HOST POSITION
        //    master.WriteSingleRegister(slaveId, 4316, (ushort)32768+2+1);
        //    Thread.Sleep(SLEEPTIME);
        //    // On réinitialise les paramètres nécessaires
        //    InitParameters(slaveId);
        //    Thread.Sleep(SLEEPTIME);
        //    SetModeHostPosition(slaveId);
        //    Thread.Sleep(SLEEPTIME);
        //    SetAcceleration();
        //    Thread.Sleep(SLEEPTIME);
        //    SetVitesse();
        //    Thread.Sleep(SLEEPTIME);
        //}



        

        //private void GoHome(byte slaveId)
        //{
        //    //master.WriteSingleRegister(slaveId, 6002, (ushort)1);
        //    master.WriteSingleRegister(slaveId, 4317, (ushort)256);
        //    Thread.Sleep(SLEEPTIME);
        //}

        //public static void DefineHome(byte slaveId)
        //{
        //    master.WriteSingleRegister(slaveId, 4316, (ushort)4096);
        //    Thread.Sleep(SLEEPTIME);
        //}

        //public static void Stop(byte slaveId)
        //{
        //    master.WriteSingleRegister(slaveId, 4317, (ushort)4);
        //}

 

        //public static string getPosition(byte slaveId)
        //{
        //    float position = 0.0f;
        //    position = Read32BitRegister(slaveId, 378);
        //    return position.ToString();
        //}

        //public static void GetParameters(byte slaveId)
        //{
        //    ushort[] acceleration = new ushort[1];
        //    ushort[] jogSlowSpeed = new ushort[1];
        //    ushort[] jogFastSpeed = new ushort[1];

        //    // Ajuste le boolean jogfast du drive 
        //    acceleration = master.ReadInputRegisters(slaveId, 6026, (ushort)1); // On lit une accéleration
        //    Thread.Sleep(SLEEPTIME);
        //    jogSlowSpeed = master.ReadInputRegisters(slaveId, 6022, (ushort)1); // On lit une vitesse
        //    Thread.Sleep(SLEEPTIME);
        //    jogFastSpeed = master.ReadInputRegisters(slaveId, 6024, (ushort)1); // On lit une vitesse
        //    Thread.Sleep(SLEEPTIME);


        //    Console.WriteLine("ACCELERATION SLAVEID : " + slaveId + " = " + acceleration[0] + " RPM/S");
        //    Console.WriteLine("JOGFAST SPEED SLAVEID : " + slaveId + " = " + jogFastSpeed[0] + " RPM");
        //    Console.WriteLine("JOGSLOWSPEED SLAVEID : " + slaveId + " = " + jogSlowSpeed[0] + " RPMS");

        //}
        //public static void GetParametersFault(byte slaveId)
        //{
        //    Console.WriteLine("Recherche d'erreurs en cours pour le ID :" + slaveId);
        //    Console.WriteLine("-----------------------------------------------");
        //    ushort[] errorCode = new ushort[1];

        //    errorCode = master.ReadInputRegisters(slaveId, 5, (ushort)1); // On lit

        //    Thread.Sleep(SLEEPTIME);
        //    //foreach (String error in ErrorParser.ParseError(errorCode[0]))
        //    //{
        //    //    Console.WriteLine(error);
        //    //}
        //    Console.WriteLine();
        //}

        //public static float Read32BitRegister(byte slaveId, ushort register)
        //{
        //    //ushort[] recieved = master.ReadInputRegisters(slaveId, register, (ushort)1);
        //    ushort[] registers = master.ReadHoldingRegisters(slaveId, register, 2);
        //    float value = 0;
        //    float v1 = registers[HIGH];
        //    float v0 = registers[LOW];
        //    value = v1;

        //    Console.WriteLine("value " + value);

        //    return value;
        //}



        //// On créer un mouvement temporaire avec les paramètres reçus.
        //EtatActuateurs EtatTmp = new EtatActuateurs(AvDroit, AvGauche, vitesse, acceleration);

        //// Si le mouvement est pour "immédiatement", on vide la liste des prochains mouvements.  (sinon, on ajoute)
        //if (immediatement)
        //    ListeProchainsEtats.Clear();

        //// On ajoute le mouvement à la liste.
        //ListeProchainsEtats.Add(EtatTmp);


        //public static void Fault(byte slaveId)
        //{
        //    ushort[] fault = new ushort[1];
        //    // J'ai changé ReadHOLDINGRegister pour READINPUTREGISTER:   Input: registre Read Only.    HoldingL: Read-Write.  Fault est Read Only.  JSD.
        //    fault = master.ReadInputRegisters(slaveId, 5, (ushort)1);
        //    if ((fault[0] & (ushort)4) == 4)
        //    {
        //        Console.WriteLine("Fault (" + fault[0] + ")");
        //    }
        //    else
        //    {
        //        Console.WriteLine("No fault (" + fault[0] + ")");
        //    }
        //}

    }
}
