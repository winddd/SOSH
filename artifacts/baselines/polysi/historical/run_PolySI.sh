JAR_FILE="/home/windkl/git_repos/PolySI3/build/libs/PolySI-1.0.0-SNAPSHOT.jar"

#LOG_FOLDER=/home/windkl/viper_logs/osdi25/cobrabench/fromCobraBenchOriginal/yuga_si
#HISTORIES=(blindw_yuga_si_10k  rubis_yuga_si_10k  tpcc_yuga_si_10k  twitter_yuga_si_10k)

LOG_FOLDER=/home/windkl/viper_logs/osdi26/cobrabench/yuga_si
HISTORIES=(
yuga_si_blindw_50k  \
yuga_si_rubis_50k  \
yuga_si_twitter_50k \
yuga_si_tpcc_50k \
  )

#LEN=1
LEN=${#HISTORIES[@]}

for(( i=0;i<$LEN;i++)) do
    echo -e "Checking ${HISTORIES[i]}\n"
    java -jar $JAR_FILE audit --type=cobra "$LOG_FOLDER/${HISTORIES[i]}"
done
