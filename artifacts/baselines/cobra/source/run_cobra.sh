LOG_FOLDER=~/viper_logs/osdi25/cobrabench/fromCobraBenchOriginal
HISTORIES=(
#   tpcc_10k
#    twitter_10k \
    rw_10k \
#    # tw_50k
#    rubis_10k
    )

for(( i=0;i<${#HISTORIES[@]};i++)) do
    echo -e "Checking ${HISTORIES[i]}\n"
    echo -e "Executing timeout 600s ./run.sh mono audit ./cobra.conf.default $LOG_FOLDER/${HISTORIES[i]}"
    timeout 600s ./run.sh mono audit ./cobra.conf.default "$LOG_FOLDER/${HISTORIES[i]}"
done
