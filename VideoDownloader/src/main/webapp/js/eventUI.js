

const TS_VALIDATION=/^([\+-]?\d{4}(?!\d{2}\b))((-?)((0[1-9]|1[0-2])(\3([12]\d|0[1-9]|3[01]))?|W([0-4]\d|5[0-2])(-?[1-7])?|(00[1-9]|0[1-9]\d|[12]\d{2}|3([0-5]\d|6[1-6])))([T\s]((([01]\d|2[0-3])((:?)[0-5]\d)?|24\:?00)([\.,]\d+(?!:))?)?(\17[0-5]\d([\.,]\d+)?)?([zZ]|([\+-])([01]\d|2[0-3]):?([0-5]\d)?)?)?)?$/
/* Thanks to John Reeve, Jennifer Payne, Michael Payne 
 * https://www.myintervals.com/blog/2009/05/20/iso-8601-date-validation-that-doesnt-suck/
 */


class Event {
    constructor(eventData) {
        this.eventId = eventData.eventId;
        this.startTimestamp = eventData.startTimestamp;
        this.endTimestamp = eventData.endTimestamp;
        this.videoSources = [];
        for (let vs of eventData.videoSources) {
            this.videoSources.push({
                'location':vs.location,
                'camera':vs.camera
            }) 
        }
    }

    getVideoURL = function(location, camera) {
        for(let source of this.videoSources) {
            if(source.location == location && source.camera==camera) {
                return `getVideo?locationName=${source.location}&cameraId=${source.camera}&startTimestamp=${this.startTimestamp}&endTimestamp=${this.endTimestamp}`;
            }
        }
    }

}

let eventCollection = new Map();


class UI {
    constructor() {
        
    }

    onload=function(){
        // Load existing events
        $.ajax({
            'url':"api/events",
            'type': "GET",
            'dataType': "json",
            'success': (response) => {
                if (response.status=="error") {
                    console.error(JSON.stringify(response));
                    return;
                }
                for(let ev of response.results){
                    $("#eventIds").append(new Option(ev, ev, ev==response.results[0]));
                }
                ui.eventSelect();
            },
            'fail': (err) => {
                console.error("Error loading events", err);
            }
        });

        $("#eventIds").change(ui.eventSelect);
        document.getElementById("eventForm").addEventListener("submit", ui.formSubmit)
        document.getElementById("fldStartTimestamp").addEventListener("input", ui.tsValidation);
        document.getElementById("fldEndTimestamp").addEventListener("input", ui.tsValidation);
        $("#cbVideoSources input[type=checkbox]").map(  (i,cb) => {
            cb.addEventListener("change", ui.videoSourceChangeHandler)
        })
    }

    formCancel = function() {
        let form = document.getElementById("eventForm");
        form.classList.remove("was-validated");
        form.reset();
    }

    formSubmit = function(e) {
        //e.preventDefault();
        //e.stopPropagation();
        let form = document.getElementById("eventForm");
        let isValid = form.checkValidity();
        if ($("#cbVideoSources input[type=checkbox]:checked").length==0){
            $("#cbVideoSources input[type=checkbox]").map( (i,cb)=>{
                cb.setCustomValidity("Al menos una fuente de video debe seleccionarse");
            })
            isValid = false
        } else {
            $("#cbVideoSources input[type=checkbox]").map( (i,cb)=>{
                cb.setCustomValidity('');
            })
        }
        form.classList.add('was-validated')
        if (isValid) {
            let eventId = $("#fldEventId").val();
            $.ajax({
                'type':"PUT",
                'url':`api/event/${eventId}`,
                'data': $("#eventForm").serialize(),
                'success': (data) => {
                    if(data.status=="success") {
                        form.reset();
                        alert("El evento se a registrado exitosamente");
                        document.location.reload();
                    } else {
                        alert("ocurrió un error")
                    }
                },
                'fail': (err) => {
                    alert(err);
                }
            })
        }
        
    }

    videoSourceChangeHandler = function() {
        let checkBoxes = $("#cbVideoSources input[type=checkbox]")
        let loc = "";
        let cam = "";
        for (let cb of checkBoxes) {
            if (cb.checked) {
                let cbVal = cb.value.split(";");
                loc = loc + (loc!="" ? "," : "") + cbVal[0]
                cam = cam + (cam!="" ? "," : "") + cbVal[1]
            }
        }
        $("#fldLocation").val(loc);
        $("#fldCamera").val(cam);

    }

    tsValidation = function(e) {
        let input = e.target;
        if (input.value.match(TS_VALIDATION)) {
            input.setCustomValidity('');
        } else {
            input.setCustomValidity('Formato de fecha/hora inválido');
        }
    }

    eventSelect = function() {
        let selEventId = $("#eventIds").find(":selected").text();
        ui.loadEvent(selEventId).then( event => {
            $("#in_startTimestamp").val(event.startTimestamp);
            $("#in_endTimestamp").val(event.endTimestamp)
            let table=document.getElementById("tableBody");
            table.innerHTML="";
            for(let ev of event.videoSources) {
                let row = table.insertRow();
                let cLoc = row.insertCell();
                let cCam = row.insertCell();
                let cBtn = row.insertCell();
                let btn = document.createElement("BUTTON");
                btn.setAttribute("onclick", `ui.showVideo("${event.eventId}","${ev.location}", "${ev.camera}")`);
                btn.setAttribute("type", "button");
                btn.setAttribute("class", "btn btn-primary")
                btn.innerHTML="Mostrar video"
                cLoc.appendChild(document.createTextNode(ev.location));
                cCam.appendChild(document.createTextNode(ev.camera));
                cBtn.appendChild(btn)
                

                $("#tableBody").append(row)
            }
        }).catch(err=>{
            console.error(err);
        });
    }

    loadEvent=function(eventId) {
        if (eventCollection.has(eventId)) {
            return Promise.resolve(eventCollection.get(eventId));
        } else {
            return new Promise( (resolve, reject) => {
                $.ajax({
                    'url':"api/event/" + eventId,
                    'type':"GET",
                    'dataType':"json",
                    'success': (response) => {
                        if(response.eventId) {
                            eventCollection.set(response.eventId, new Event(response));
                            resolve(eventCollection.get(response.eventId));
                        } else {
                            reject(new Error(eventResponse.message));
                        }
                    },
                    'fail': (err) => { reject(err); }                    
                })
            })
        }
    }

    showVideo = function(eventId, location, camera) {
        let event = eventCollection.get(eventId);
        let vSrc = event.getVideoURL(location,camera);
        $("#videoContainer").html(`<video controls><source src="${vSrc}" type="video/mp4"></video>`);
    }

}

const ui = new UI();
window.addEventListener("load", ui.onload)