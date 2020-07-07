FROM python:3.8
USER 0
#RUN yum install -y --disableplugin=subscription-manager libXrender 

ADD --chown=1001:0 src/* /pyvg/app/
ADD --chown=1001:0 conf/* /pyvg/conf/
ADD requirements.txt /pyvg/

RUN python -m pip install -r /pyvg/requirements.txt
USER 1001
WORKDIR /pyvg/app
CMD ["python", "main.py"]
